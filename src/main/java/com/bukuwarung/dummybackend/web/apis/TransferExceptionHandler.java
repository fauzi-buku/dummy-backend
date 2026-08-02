package com.bukuwarung.dummybackend.web.apis;

import com.bukuwarung.dummybackend.usecases.initiate_transfer_batch.IdempotencyConflictException;
import com.bukuwarung.dummybackend.usecases.initiate_transfer_batch.RequestInProgressException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class TransferExceptionHandler {

  @ExceptionHandler(IdempotencyConflictException.class)
  public ResponseEntity<ErrorResponse> handleIdempotencyConflict(IdempotencyConflictException e) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(e.getMessage()));
  }

  @ExceptionHandler(RequestInProgressException.class)
  public ResponseEntity<ErrorResponse> handleRequestInProgress(RequestInProgressException e) {
    return ResponseEntity.status(HttpStatus.TOO_EARLY).body(new ErrorResponse(e.getMessage()));
  }
}
