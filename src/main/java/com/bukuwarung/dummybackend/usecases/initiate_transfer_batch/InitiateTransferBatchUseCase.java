package com.bukuwarung.dummybackend.usecases.initiate_transfer_batch;

import com.bukuwarung.dummybackend.domain.dtos.InitiateTransferBatchRequest;
import com.bukuwarung.dummybackend.domain.dtos.TransferBatchResponseDTO;
import com.bukuwarung.dummybackend.domain.dtos.TransferItemRequestDTO;
import com.bukuwarung.dummybackend.domain.entities.IdempotencyKey;
import com.bukuwarung.dummybackend.domain.entities.TransferBatch;
import com.bukuwarung.dummybackend.domain.entities.TransferItem;
import com.bukuwarung.dummybackend.domain.entities.enums.BatchStatus;
import com.bukuwarung.dummybackend.domain.entities.enums.IdempotencyStatus;
import com.bukuwarung.dummybackend.domain.entities.enums.TransferItemStatus;
import com.bukuwarung.dummybackend.repositories.TransferBatchRepository;
import com.bukuwarung.dummybackend.repositories.TransferItemRepository;
import com.bukuwarung.dummybackend.utils.MapperUtil;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class InitiateTransferBatchUseCase {

  public static final String SCOPE = "POST /api/transfer/batch";

  private final IdempotencyKeyService idempotencyKeyService;
  private final TransferItemProcessingService transferItemProcessingService;
  private final TransferBatchRepository transferBatchRepository;
  private final TransferItemRepository transferItemRepository;
  private final MapperUtil mapperUtil;
  private final ObjectMapper objectMapper;

  public InitiateTransferBatchUseCase(
      IdempotencyKeyService idempotencyKeyService,
      TransferItemProcessingService transferItemProcessingService,
      TransferBatchRepository transferBatchRepository,
      TransferItemRepository transferItemRepository,
      MapperUtil mapperUtil,
      ObjectMapper objectMapper) {
    this.idempotencyKeyService = idempotencyKeyService;
    this.transferItemProcessingService = transferItemProcessingService;
    this.transferBatchRepository = transferBatchRepository;
    this.transferItemRepository = transferItemRepository;
    this.mapperUtil = mapperUtil;
    this.objectMapper = objectMapper;
  }

  public TransferBatchResponseDTO execute(
      String idempotencyKeyHeader, InitiateTransferBatchRequest request) {
    String fingerprint = fingerprint(request);

    UUID activeKeyId;
    TransferBatch batch;

    Optional<IdempotencyKey> claimed = tryClaim(idempotencyKeyHeader, fingerprint);
    if (claimed.isPresent()) {
      activeKeyId = claimed.get().getId();
      batch = createBatchWithItems(request);
      processNonTerminalItems(batch);
    } else {
      IdempotencyKey existing =
          idempotencyKeyService
              .findExisting(idempotencyKeyHeader, SCOPE)
              .orElseThrow(
                  () -> new IllegalStateException("Idempotency key vanished unexpectedly"));

      if (!existing.getRequestFingerprint().equals(fingerprint)) {
        throw new IdempotencyConflictException(
            "Idempotency-Key '"
                + idempotencyKeyHeader
                + "' was already used with a different request payload");
      }
      activeKeyId = existing.getId();

      if (existing.getStatus() == IdempotencyStatus.IN_PROGRESS) {
        if (idempotencyKeyService.isLeaseExpired(existing) && stealExpiredLease(existing.getId())) {
          batch = getBatch(existing.getResourceId());
          processNonTerminalItems(batch);
        } else {
          throw new RequestInProgressException(
              "Request with Idempotency-Key '"
                  + idempotencyKeyHeader
                  + "' is already being processed");
        }
      } else {
        batch = getBatch(existing.getResourceId());
        List<TransferItem> items =
            transferItemRepository.findByBatch_IdOrderByItemIndex(batch.getId());
        boolean hasRetryableItems = items.stream().anyMatch(TransferItem::isRetryEligible);
        if (hasRetryableItems && reopenCompleted(existing.getId())) {
          processNonTerminalItems(batch);
        } else {
          return deserialize(existing.getResponseBody());
        }
      }
    }

    List<TransferItem> items = transferItemRepository.findByBatch_IdOrderByItemIndex(batch.getId());
    batch.setStatus(computeBatchStatus(items));
    transferBatchRepository.save(batch);

    TransferBatchResponseDTO response = mapperUtil.mapToTransferBatchResponse(batch, items);
    idempotencyKeyService.complete(activeKeyId, 200, toJson(response), batch.getId());
    return response;
  }

  private Optional<IdempotencyKey> tryClaim(String idempotencyKeyHeader, String fingerprint) {
    try {
      return Optional.of(
          idempotencyKeyService.insertNewClaim(idempotencyKeyHeader, SCOPE, fingerprint));
    } catch (DataIntegrityViolationException e) {
      return Optional.empty();
    }
  }

  private boolean stealExpiredLease(UUID id) {
    try {
      return idempotencyKeyService.stealExpiredLease(id) != null;
    } catch (ObjectOptimisticLockingFailureException e) {
      return false;
    }
  }

  private boolean reopenCompleted(UUID id) {
    try {
      return idempotencyKeyService.reopenCompleted(id) != null;
    } catch (ObjectOptimisticLockingFailureException e) {
      return false;
    }
  }

  private TransferBatch createBatchWithItems(InitiateTransferBatchRequest request) {
    TransferBatch batch = new TransferBatch();
    batch.setStatus(BatchStatus.PROCESSING);
    batch = transferBatchRepository.save(batch);

    List<TransferItemRequestDTO> transfers = request.getTransfers();
    List<TransferItem> items = new ArrayList<>();
    for (int i = 0; i < transfers.size(); i++) {
      TransferItemRequestDTO dto = transfers.get(i);
      TransferItem item = new TransferItem();
      item.setBatch(batch);
      item.setItemIndex(i);
      item.setClientReference(dto.getClientReference());
      item.setSourceAccountNumber(dto.getSourceAccountNumber());
      item.setDestinationAccountNumber(dto.getDestinationAccountNumber());
      item.setAmount(dto.getAmount());
      item.setCurrency(dto.getCurrency());
      item.setStatus(TransferItemStatus.PENDING);
      item.setAttemptCount(0);
      item.setBankIdempotencyKey(batch.getId() + ":" + i);
      items.add(item);
    }
    transferItemRepository.saveAll(items);
    return batch;
  }

  private void processNonTerminalItems(TransferBatch batch) {
    List<TransferItem> items = transferItemRepository.findByBatch_IdOrderByItemIndex(batch.getId());
    for (TransferItem item : items) {
      if (item.isRetryEligible()) {
        transferItemProcessingService.processItem(item.getId());
      }
    }
  }

  private TransferBatch getBatch(UUID batchId) {
    return transferBatchRepository.findById(batchId).orElseThrow();
  }

  private BatchStatus computeBatchStatus(List<TransferItem> items) {
    boolean allSucceeded =
        items.stream().allMatch(item -> item.getStatus() == TransferItemStatus.SUCCEEDED);
    if (allSucceeded) {
      return BatchStatus.COMPLETED;
    }
    boolean allFailed =
        items.stream().allMatch(item -> item.getStatus() == TransferItemStatus.FAILED);
    return allFailed ? BatchStatus.FAILED : BatchStatus.PARTIALLY_FAILED;
  }

  private String fingerprint(InitiateTransferBatchRequest request) {
    String json = objectMapper.writeValueAsString(request);
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(json.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("Failed to fingerprint request", e);
    }
  }

  private String toJson(TransferBatchResponseDTO response) {
    return objectMapper.writeValueAsString(response);
  }

  private TransferBatchResponseDTO deserialize(String json) {
    return objectMapper.readValue(json, TransferBatchResponseDTO.class);
  }
}
