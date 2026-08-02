package com.bukuwarung.dummybackend.domain.dtos;

import com.bukuwarung.dummybackend.domain.entities.enums.BatchStatus;
import java.util.List;
import java.util.UUID;

public class TransferBatchResponseDTO {

  private UUID batchId;
  private BatchStatus status;
  private List<TransferItemResponseDTO> items;

  public UUID getBatchId() {
    return batchId;
  }

  public void setBatchId(UUID batchId) {
    this.batchId = batchId;
  }

  public BatchStatus getStatus() {
    return status;
  }

  public void setStatus(BatchStatus status) {
    this.status = status;
  }

  public List<TransferItemResponseDTO> getItems() {
    return items;
  }

  public void setItems(List<TransferItemResponseDTO> items) {
    this.items = items;
  }
}
