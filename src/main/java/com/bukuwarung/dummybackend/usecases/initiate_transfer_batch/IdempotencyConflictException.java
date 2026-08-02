package com.bukuwarung.dummybackend.usecases.initiate_transfer_batch;

public class IdempotencyConflictException extends RuntimeException {

  public IdempotencyConflictException(String message) {
    super(message);
  }
}
