package com.bukuwarung.dummybackend.integrations.bank;

import java.math.BigDecimal;

public interface BankGatewayClient {

  BankTransferResult transfer(
      String bankIdempotencyKey,
      String sourceAccountNumber,
      String destinationAccountNumber,
      BigDecimal amount,
      String currency);
}
