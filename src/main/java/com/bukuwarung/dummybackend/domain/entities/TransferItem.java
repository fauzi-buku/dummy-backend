package com.bukuwarung.dummybackend.domain.entities;

import com.bukuwarung.dummybackend.domain.entities.enums.TransferItemStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

@Table(
    name = "transfer_items",
    uniqueConstraints = @UniqueConstraint(columnNames = {"batch_id", "item_index"}))
@Entity
public class TransferItem extends BaseEntity {

  public static final int MAX_ATTEMPTS = 3;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "batch_id", nullable = false)
  @NotNull
  private TransferBatch batch;

  @Column(name = "item_index", nullable = false)
  private int itemIndex;

  @Column(name = "client_reference", nullable = false)
  @NotBlank
  private String clientReference;

  @Column(name = "source_account_number", nullable = false)
  @NotBlank
  private String sourceAccountNumber;

  @Column(name = "destination_account_number", nullable = false)
  @NotBlank
  private String destinationAccountNumber;

  @Column(name = "amount", nullable = false, precision = 19, scale = 2)
  @NotNull
  private BigDecimal amount;

  @Column(name = "currency", nullable = false)
  @NotBlank
  private String currency;

  @Column(name = "status", nullable = false)
  @Enumerated(EnumType.STRING)
  @NotNull
  private TransferItemStatus status;

  @Column(name = "bank_reference")
  private String bankReference;

  @Column(name = "failure_reason")
  private String failureReason;

  @Column(name = "bank_idempotency_key", nullable = false, unique = true)
  @NotBlank
  private String bankIdempotencyKey;

  @Column(name = "attempt_count", nullable = false)
  private int attemptCount;

  public boolean isRetryEligible() {
    return switch (status) {
      case PENDING, PROCESSING -> true;
      case FAILED -> attemptCount < MAX_ATTEMPTS;
      case SUCCEEDED -> false;
    };
  }

  public TransferBatch getBatch() {
    return batch;
  }

  public void setBatch(TransferBatch batch) {
    this.batch = batch;
  }

  public int getItemIndex() {
    return itemIndex;
  }

  public void setItemIndex(int itemIndex) {
    this.itemIndex = itemIndex;
  }

  public String getClientReference() {
    return clientReference;
  }

  public void setClientReference(String clientReference) {
    this.clientReference = clientReference;
  }

  public String getSourceAccountNumber() {
    return sourceAccountNumber;
  }

  public void setSourceAccountNumber(String sourceAccountNumber) {
    this.sourceAccountNumber = sourceAccountNumber;
  }

  public String getDestinationAccountNumber() {
    return destinationAccountNumber;
  }

  public void setDestinationAccountNumber(String destinationAccountNumber) {
    this.destinationAccountNumber = destinationAccountNumber;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public void setAmount(BigDecimal amount) {
    this.amount = amount;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
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

  public String getBankIdempotencyKey() {
    return bankIdempotencyKey;
  }

  public void setBankIdempotencyKey(String bankIdempotencyKey) {
    this.bankIdempotencyKey = bankIdempotencyKey;
  }

  public int getAttemptCount() {
    return attemptCount;
  }

  public void setAttemptCount(int attemptCount) {
    this.attemptCount = attemptCount;
  }
}
