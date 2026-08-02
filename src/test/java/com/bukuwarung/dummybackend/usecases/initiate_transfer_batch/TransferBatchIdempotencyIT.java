package com.bukuwarung.dummybackend.usecases.initiate_transfer_batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.bukuwarung.dummybackend.bootstrap.DummyBackend;
import com.bukuwarung.dummybackend.domain.dtos.InitiateTransferBatchRequest;
import com.bukuwarung.dummybackend.domain.dtos.TransferBatchResponseDTO;
import com.bukuwarung.dummybackend.domain.dtos.TransferItemRequestDTO;
import com.bukuwarung.dummybackend.domain.entities.IdempotencyKey;
import com.bukuwarung.dummybackend.domain.entities.TransferItem;
import com.bukuwarung.dummybackend.domain.entities.enums.BatchStatus;
import com.bukuwarung.dummybackend.domain.entities.enums.IdempotencyStatus;
import com.bukuwarung.dummybackend.domain.entities.enums.TransferItemStatus;
import com.bukuwarung.dummybackend.integrations.bank.SimulatedBankGatewayClient;
import com.bukuwarung.dummybackend.repositories.IdempotencyKeyRepository;
import com.bukuwarung.dummybackend.repositories.TransferBatchRepository;
import com.bukuwarung.dummybackend.repositories.TransferItemRepository;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(classes = DummyBackend.class)
@Testcontainers
class TransferBatchIdempotencyIT {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:14-alpine");

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private IdempotencyKeyRepository idempotencyKeyRepository;
  @Autowired private TransferBatchRepository transferBatchRepository;
  @Autowired private TransferItemRepository transferItemRepository;
  @Autowired private SimulatedBankGatewayClient bankGatewayClient;
  @Autowired private IdempotencyKeyService idempotencyKeyService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    transferItemRepository.deleteAll();
    transferBatchRepository.deleteAll();
    idempotencyKeyRepository.deleteAll();
  }

  @Test
  void sameKeySamePayload_replaysCachedResponse_whenAllItemsSucceed() throws Exception {
    InitiateTransferBatchRequest request =
        batchRequest(
            List.of(item("ref-1", "GOODACCT01", "10000"), item("ref-2", "GOODACCT02", "20000")));

    BatchCallResult first = postBatch("key-success", request);
    BatchCallResult second = postBatch("key-success", request);

    assertThat(first.status()).isEqualTo(HttpStatus.OK);
    assertThat(second.status()).isEqualTo(HttpStatus.OK);
    assertThat(second.body().getBatchId()).isEqualTo(first.body().getBatchId());
    assertThat(second.body().getStatus()).isEqualTo(BatchStatus.COMPLETED);

    assertThat(transferBatchRepository.count()).isEqualTo(1);
    assertThat(transferItemRepository.count()).isEqualTo(2);

    UUID batchId = first.body().getBatchId();
    assertThat(bankGatewayClient.executionCountFor(batchId + ":0")).isEqualTo(1);
    assertThat(bankGatewayClient.executionCountFor(batchId + ":1")).isEqualTo(1);
  }

  @Test
  void sameKeyDifferentPayload_returnsConflict() throws Exception {
    InitiateTransferBatchRequest requestA =
        batchRequest(List.of(item("ref-1", "GOODACCT01", "10000")));
    InitiateTransferBatchRequest requestB =
        batchRequest(List.of(item("ref-1", "GOODACCT01", "99999")));

    BatchCallResult first = postBatch("key-conflict", requestA);
    BatchCallResult second = postBatch("key-conflict", requestB);

    assertThat(first.status()).isEqualTo(HttpStatus.OK);
    assertThat(second.status()).isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void concurrentClaims_onlyOneWinsTheMutex() throws Exception {
    String key = "key-concurrent";
    String scope = InitiateTransferBatchUseCase.SCOPE;
    String fingerprint = "same-fingerprint";

    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch readyLatch = new CountDownLatch(2);
    CountDownLatch startLatch = new CountDownLatch(1);

    Callable<Boolean> attempt =
        () -> {
          readyLatch.countDown();
          startLatch.await();
          try {
            idempotencyKeyService.insertNewClaim(key, scope, fingerprint);
            return true;
          } catch (DataIntegrityViolationException e) {
            return false;
          }
        };

    Future<Boolean> first = executor.submit(attempt);
    Future<Boolean> second = executor.submit(attempt);
    readyLatch.await();
    startLatch.countDown();

    boolean firstWon = first.get(5, TimeUnit.SECONDS);
    boolean secondWon = second.get(5, TimeUnit.SECONDS);
    executor.shutdown();

    assertThat(firstWon ^ secondWon).isTrue();
    assertThat(idempotencyKeyRepository.count()).isEqualTo(1);
  }

  @Test
  void mixedBatch_partiallyFails_whenSomeItemsHitMagicFailureAccount() throws Exception {
    List<TransferItemRequestDTO> items = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      items.add(item("ok-" + i, "GOODACCT0" + i, "1000"));
    }
    for (int i = 0; i < 5; i++) {
      items.add(
          item("fail-" + i, SimulatedBankGatewayClient.MAGIC_FAILING_DESTINATION_ACCOUNT, "1000"));
    }

    BatchCallResult result = postBatch("key-partial", batchRequest(items));

    assertThat(result.status()).isEqualTo(HttpStatus.OK);
    TransferBatchResponseDTO body = result.body();
    assertThat(body.getStatus()).isEqualTo(BatchStatus.PARTIALLY_FAILED);

    long succeeded =
        body.getItems().stream().filter(i -> i.getStatus() == TransferItemStatus.SUCCEEDED).count();
    long failed =
        body.getItems().stream().filter(i -> i.getStatus() == TransferItemStatus.FAILED).count();
    assertThat(succeeded).isEqualTo(5);
    assertThat(failed).isEqualTo(5);
    body.getItems().stream()
        .filter(i -> i.getStatus() == TransferItemStatus.FAILED)
        .forEach(i -> assertThat(i.getFailureReason()).isNotBlank());
  }

  @Test
  void retryingSameKey_onlyReprocessesFailedItems_neverTouchesSucceededOnes() throws Exception {
    List<TransferItemRequestDTO> items = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      items.add(item("ok-" + i, "GOODACCT0" + i, "1000"));
    }
    for (int i = 0; i < 5; i++) {
      items.add(
          item("fail-" + i, SimulatedBankGatewayClient.MAGIC_FAILING_DESTINATION_ACCOUNT, "1000"));
    }
    InitiateTransferBatchRequest request = batchRequest(items);
    String idempotencyKey = "key-retry";

    TransferBatchResponseDTO first = postBatch(idempotencyKey, request).body();
    UUID batchId = first.getBatchId();

    List<TransferItem> afterFirstCall = itemsOf(batchId);
    List<TransferItem> succeededItems =
        filterByStatus(afterFirstCall, TransferItemStatus.SUCCEEDED);
    List<TransferItem> failedItems = filterByStatus(afterFirstCall, TransferItemStatus.FAILED);
    assertThat(succeededItems).hasSize(5);
    assertThat(failedItems).hasSize(5);
    succeededItems.forEach(
        i ->
            assertThat(bankGatewayClient.executionCountFor(i.getBankIdempotencyKey()))
                .isEqualTo(1));
    failedItems.forEach(
        i ->
            assertThat(bankGatewayClient.executionCountFor(i.getBankIdempotencyKey()))
                .isEqualTo(1));

    // Retry #1 (attemptCount 1 -> 2, still below MAX_ATTEMPTS)
    TransferBatchResponseDTO second = postBatch(idempotencyKey, request).body();
    assertThat(second.getBatchId()).isEqualTo(batchId);
    assertThat(second.getStatus()).isEqualTo(BatchStatus.PARTIALLY_FAILED);
    assertRetryRound(batchId, succeededItems, 2);

    // Retry #2 (attemptCount 2 -> 3, reaches MAX_ATTEMPTS)
    postBatch(idempotencyKey, request);
    assertRetryRound(batchId, succeededItems, 3);

    // Retry #3: failed items have exhausted MAX_ATTEMPTS, so they must no longer be retried
    postBatch(idempotencyKey, request);
    List<TransferItem> afterExhaustion = itemsOf(batchId);
    filterByStatus(afterExhaustion, TransferItemStatus.FAILED)
        .forEach(i -> assertThat(i.getAttemptCount()).isEqualTo(3));
    assertAllBankExecutionCountsStayAtOne(afterExhaustion);
  }

  // attemptCount (our own bookkeeping) climbs on every retry of a failed item, proving the
  // endpoint really does retry it. executionCountFor stays at 1 regardless: the simulated bank
  // dedupes by bankIdempotencyKey, so it never actually re-executes for the same item - that is
  // the layer-4 defense working as intended, on both failed and already-succeeded items alike.
  private void assertRetryRound(
      UUID batchId, List<TransferItem> originalSucceededItems, int expectedAttemptCount) {
    List<TransferItem> current = itemsOf(batchId);
    filterByStatus(current, TransferItemStatus.FAILED)
        .forEach(i -> assertThat(i.getAttemptCount()).isEqualTo(expectedAttemptCount));
    assertAllBankExecutionCountsStayAtOne(current);

    // re-fetch (not the stale in-memory copies) to prove succeeded items were truly never
    // touched by this retry round, not just that our old references didn't change.
    List<String> succeededClientRefs =
        originalSucceededItems.stream().map(TransferItem::getClientReference).toList();
    filterByStatus(current, TransferItemStatus.SUCCEEDED).stream()
        .filter(i -> succeededClientRefs.contains(i.getClientReference()))
        .forEach(i -> assertThat(i.getAttemptCount()).isEqualTo(1));
  }

  private void assertAllBankExecutionCountsStayAtOne(List<TransferItem> items) {
    items.forEach(
        i ->
            assertThat(bankGatewayClient.executionCountFor(i.getBankIdempotencyKey()))
                .isEqualTo(1));
  }

  @Test
  void resumeAfterCrash_onlyProcessesPendingItem_leavesSucceededItemUntouched() throws Exception {
    InitiateTransferBatchRequest request =
        batchRequest(
            List.of(item("ref-1", "GOODACCT01", "1000"), item("ref-2", "GOODACCT02", "2000")));
    String idempotencyKey = "key-crash";

    TransferBatchResponseDTO original = postBatch(idempotencyKey, request).body();
    UUID batchId = original.getBatchId();
    assertThat(original.getStatus()).isEqualTo(BatchStatus.COMPLETED);

    List<TransferItem> items = itemsOf(batchId);
    TransferItem alreadySucceededItem = items.get(0);
    TransferItem itemToRewind = items.get(1);
    String rewoundBankKey = itemToRewind.getBankIdempotencyKey();
    String originalBankReference = itemToRewind.getBankReference();
    assertThat(bankGatewayClient.executionCountFor(rewoundBankKey)).isEqualTo(1);

    // Simulate a crash that happened after item 2 was sent to the bank but before the outcome
    // was durably recorded and before the idempotency key was marked COMPLETED.
    itemToRewind.setStatus(TransferItemStatus.PENDING);
    itemToRewind.setBankReference(null);
    transferItemRepository.save(itemToRewind);

    IdempotencyKey idempotencyKeyRow =
        idempotencyKeyRepository
            .findByIdempotencyKeyAndScope(idempotencyKey, InitiateTransferBatchUseCase.SCOPE)
            .orElseThrow();
    idempotencyKeyRow.setStatus(IdempotencyStatus.IN_PROGRESS);
    idempotencyKeyRow.setLeaseExpiresAt(ZonedDateTime.now().minusMinutes(5));
    idempotencyKeyRepository.save(idempotencyKeyRow);

    BatchCallResult resumed = postBatch(idempotencyKey, request);

    assertThat(resumed.status()).isEqualTo(HttpStatus.OK);
    assertThat(resumed.body().getBatchId()).isEqualTo(batchId);
    assertThat(resumed.body().getStatus()).isEqualTo(BatchStatus.COMPLETED);

    // The already-succeeded item was never touched by the resume: our own status guard skips it,
    // and even if it hadn't, the bank's own ledger still dedupes by this key.
    assertThat(bankGatewayClient.executionCountFor(alreadySucceededItem.getBankIdempotencyKey()))
        .isEqualTo(1);
    TransferItem reloadedSucceeded =
        transferItemRepository.findById(alreadySucceededItem.getId()).orElseThrow();
    assertThat(reloadedSucceeded.getBankReference())
        .isEqualTo(alreadySucceededItem.getBankReference());

    // The rewound item WAS resubmitted to the bank (attemptCount incremented), but since it uses
    // the same bankIdempotencyKey as its original (never-crashed) attempt, the bank replays its
    // cached success instead of transferring the money a second time.
    assertThat(bankGatewayClient.executionCountFor(rewoundBankKey)).isEqualTo(1);
    TransferItem reloadedRewound =
        transferItemRepository.findById(itemToRewind.getId()).orElseThrow();
    assertThat(reloadedRewound.getStatus()).isEqualTo(TransferItemStatus.SUCCEEDED);
    assertThat(reloadedRewound.getBankReference()).isEqualTo(originalBankReference);
    assertThat(reloadedRewound.getAttemptCount()).isEqualTo(2);
  }

  private List<TransferItem> itemsOf(UUID batchId) {
    return transferItemRepository.findByBatch_IdOrderByItemIndex(batchId);
  }

  private List<TransferItem> filterByStatus(List<TransferItem> items, TransferItemStatus status) {
    return items.stream().filter(i -> i.getStatus() == status).toList();
  }

  private BatchCallResult postBatch(String idempotencyKey, InitiateTransferBatchRequest request)
      throws Exception {
    String json = objectMapper.writeValueAsString(request);
    MockHttpServletResponse response =
        mockMvc
            .perform(
                post("/api/transfer/batch")
                    .header("Idempotency-Key", idempotencyKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json))
            .andReturn()
            .getResponse();
    String content = response.getContentAsString();
    TransferBatchResponseDTO body =
        content.isBlank() ? null : objectMapper.readValue(content, TransferBatchResponseDTO.class);
    return new BatchCallResult(HttpStatus.valueOf(response.getStatus()), body);
  }

  private record BatchCallResult(HttpStatus status, TransferBatchResponseDTO body) {}

  private TransferItemRequestDTO item(
      String clientReference, String destinationAccount, String amount) {
    TransferItemRequestDTO dto = new TransferItemRequestDTO();
    dto.setClientReference(clientReference);
    dto.setSourceAccountNumber("SRC0001");
    dto.setDestinationAccountNumber(destinationAccount);
    dto.setAmount(new BigDecimal(amount));
    dto.setCurrency("IDR");
    return dto;
  }

  private InitiateTransferBatchRequest batchRequest(List<TransferItemRequestDTO> items) {
    InitiateTransferBatchRequest request = new InitiateTransferBatchRequest();
    request.setTransfers(items);
    return request;
  }
}
