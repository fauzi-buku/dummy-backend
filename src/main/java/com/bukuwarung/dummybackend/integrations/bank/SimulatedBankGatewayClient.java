package com.bukuwarung.dummybackend.integrations.bank;

import com.github.f4b6a3.uuid.UuidCreator;
import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

// Dedupes by bankIdempotencyKey so a repeated call for the same item never moves money twice.
@Component
public class SimulatedBankGatewayClient implements BankGatewayClient {

  public static final String MAGIC_FAILING_DESTINATION_ACCOUNT = "0000000000";

  private final Map<String, BankTransferResult> ledger = new ConcurrentHashMap<>();
  private final Map<String, AtomicInteger> executionCounts = new ConcurrentHashMap<>();

  @Override
  public BankTransferResult transfer(
      String bankIdempotencyKey,
      String sourceAccountNumber,
      String destinationAccountNumber,
      BigDecimal amount,
      String currency) {
    return ledger.computeIfAbsent(
        bankIdempotencyKey,
        key -> {
          executionCounts.computeIfAbsent(key, k -> new AtomicInteger()).incrementAndGet();
          if (MAGIC_FAILING_DESTINATION_ACCOUNT.equals(destinationAccountNumber)) {
            return BankTransferResult.failure("Destination account rejected the transfer");
          }
          return BankTransferResult.success(UuidCreator.getTimeOrderedEpoch().toString());
        });
  }

  public int executionCountFor(String bankIdempotencyKey) {
    return executionCounts.getOrDefault(bankIdempotencyKey, new AtomicInteger()).get();
  }
}
