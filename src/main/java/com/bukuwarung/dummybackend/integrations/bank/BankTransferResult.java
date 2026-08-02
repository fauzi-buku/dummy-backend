package com.bukuwarung.dummybackend.integrations.bank;

public record BankTransferResult(boolean success, String bankReference, String failureReason) {

  public static BankTransferResult success(String bankReference) {
    return new BankTransferResult(true, bankReference, null);
  }

  public static BankTransferResult failure(String failureReason) {
    return new BankTransferResult(false, null, failureReason);
  }
}
