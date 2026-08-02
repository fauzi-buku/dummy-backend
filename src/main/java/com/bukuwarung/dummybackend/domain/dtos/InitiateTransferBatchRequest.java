package com.bukuwarung.dummybackend.domain.dtos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public class InitiateTransferBatchRequest {

  @NotEmpty @Valid private List<TransferItemRequestDTO> transfers;

  public List<TransferItemRequestDTO> getTransfers() {
    return transfers;
  }

  public void setTransfers(List<TransferItemRequestDTO> transfers) {
    this.transfers = transfers;
  }
}
