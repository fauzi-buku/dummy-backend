package com.bukuwarung.dummybackend.domain.entities;

import com.bukuwarung.dummybackend.domain.entities.enums.BatchStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

@Table(name = "transfer_batches")
@Entity
public class TransferBatch extends BaseEntity {

  @Column(name = "status", nullable = false)
  @Enumerated(EnumType.STRING)
  @NotNull
  private BatchStatus status;

  public BatchStatus getStatus() {
    return status;
  }

  public void setStatus(BatchStatus status) {
    this.status = status;
  }
}
