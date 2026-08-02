package com.bukuwarung.dummybackend.domain.dtos;

import com.bukuwarung.dummybackend.domain.entities.enums.TransferItemStatus;

public class TransferItemResponseDTO {

  private String clientReference;
  private TransferItemStatus status;
  private String bankReference;
  private String failureReason;

  public String getClientReference() {
    return clientReference;
  }

  public void setClientReference(String clientReference) {
    this.clientReference = clientReference;
  }

  public TransferItemStatus getStatus() {
    return status;
  }

  public void setStatus(TransferItemStatus status) {
    this.status = status;
  }

  public String getBankReference() {
    return bankReference;
  }

  public void setBankReference(String bankReference) {
    this.bankReference = bankReference;
  }

  public String getFailureReason() {
    return failureReason;
  }

  public void setFailureReason(String failureReason) {
    this.failureReason = failureReason;
  }
}
