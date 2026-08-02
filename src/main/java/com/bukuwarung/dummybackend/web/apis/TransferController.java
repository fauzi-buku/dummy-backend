package com.bukuwarung.dummybackend.web.apis;

import com.bukuwarung.dummybackend.domain.dtos.InitiateTransferBatchRequest;
import com.bukuwarung.dummybackend.domain.dtos.TransferBatchResponseDTO;
import com.bukuwarung.dummybackend.usecases.initiate_transfer_batch.InitiateTransferBatchUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/transfer")
public class TransferController {

  private final InitiateTransferBatchUseCase initiateTransferBatchUseCase;

  public TransferController(InitiateTransferBatchUseCase initiateTransferBatchUseCase) {
    this.initiateTransferBatchUseCase = initiateTransferBatchUseCase;
  }

  @PostMapping("/batch")
  public TransferBatchResponseDTO initiateBatch(
      @RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey,
      @Valid @RequestBody InitiateTransferBatchRequest request) {
    return initiateTransferBatchUseCase.execute(idempotencyKey, request);
  }
}
