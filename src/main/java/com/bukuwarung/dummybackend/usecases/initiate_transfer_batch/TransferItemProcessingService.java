package com.bukuwarung.dummybackend.usecases.initiate_transfer_batch;

import com.bukuwarung.dummybackend.domain.entities.TransferItem;
import com.bukuwarung.dummybackend.domain.entities.enums.TransferItemStatus;
import com.bukuwarung.dummybackend.integrations.bank.BankGatewayClient;
import com.bukuwarung.dummybackend.integrations.bank.BankTransferResult;
import com.bukuwarung.dummybackend.repositories.TransferItemRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferItemProcessingService {

  private final TransferItemRepository transferItemRepository;
  private final BankGatewayClient bankGatewayClient;

  public TransferItemProcessingService(
      TransferItemRepository transferItemRepository, BankGatewayClient bankGatewayClient) {
    this.transferItemRepository = transferItemRepository;
    this.bankGatewayClient = bankGatewayClient;
  }

  /**
   * Runs in its own transaction, committed independently of every other item in the batch, so that
   * a crash partway through a batch leaves already-succeeded items durably recorded instead of
   * rolled back together with the rest.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void processItem(UUID itemId) {
    TransferItem item = transferItemRepository.findById(itemId).orElseThrow();
    if (!item.isRetryEligible()) {
      return;
    }

    item.setStatus(TransferItemStatus.PROCESSING);
    item.setAttemptCount(item.getAttemptCount() + 1);
    transferItemRepository.saveAndFlush(item);

    BankTransferResult result =
        bankGatewayClient.transfer(
            item.getBankIdempotencyKey(),
            item.getSourceAccountNumber(),
            item.getDestinationAccountNumber(),
            item.getAmount(),
            item.getCurrency());

    if (result.success()) {
      item.setStatus(TransferItemStatus.SUCCEEDED);
      item.setBankReference(result.bankReference());
      item.setFailureReason(null);
    } else {
      item.setStatus(TransferItemStatus.FAILED);
      item.setFailureReason(result.failureReason());
    }
    transferItemRepository.save(item);
  }
}
