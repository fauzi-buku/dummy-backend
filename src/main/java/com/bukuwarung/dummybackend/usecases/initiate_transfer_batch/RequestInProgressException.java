package com.bukuwarung.dummybackend.usecases.initiate_transfer_batch;

public class RequestInProgressException extends RuntimeException {

  public RequestInProgressException(String message) {
    super(message);
  }
}
