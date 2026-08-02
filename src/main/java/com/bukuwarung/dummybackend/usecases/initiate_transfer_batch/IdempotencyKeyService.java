package com.bukuwarung.dummybackend.usecases.initiate_transfer_batch;

import com.bukuwarung.dummybackend.domain.entities.IdempotencyKey;
import com.bukuwarung.dummybackend.domain.entities.enums.IdempotencyStatus;
import com.bukuwarung.dummybackend.repositories.IdempotencyKeyRepository;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every write here runs in its own REQUIRES_NEW transaction and lets constraint/version-conflict
 * exceptions propagate uncaught, so the transaction rolls back cleanly on failure. Catching those
 * exceptions inside the same transactional method would leave it marked rollback-only per the JPA
 * spec, and a subsequent normal return would then fail the commit with UnexpectedRollbackException
 * instead of the caller ever seeing the real cause. Callers are expected to catch the exception
 * around the call to this service.
 */
@Service
public class IdempotencyKeyService {

  private static final Duration LEASE_DURATION = Duration.ofSeconds(30);

  private final IdempotencyKeyRepository idempotencyKeyRepository;

  public IdempotencyKeyService(IdempotencyKeyRepository idempotencyKeyRepository) {
    this.idempotencyKeyRepository = idempotencyKeyRepository;
  }

  /**
   * Inserts a fresh row. The (idempotencyKey, scope) unique constraint acts as the mutex: if
   * another request already owns this key, this throws DataIntegrityViolationException.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public IdempotencyKey insertNewClaim(String key, String scope, String requestFingerprint) {
    IdempotencyKey idempotencyKey = new IdempotencyKey();
    idempotencyKey.setIdempotencyKey(key);
    idempotencyKey.setScope(scope);
    idempotencyKey.setRequestFingerprint(requestFingerprint);
    idempotencyKey.setStatus(IdempotencyStatus.IN_PROGRESS);
    idempotencyKey.setLeaseExpiresAt(ZonedDateTime.now().plus(LEASE_DURATION));
    return idempotencyKeyRepository.saveAndFlush(idempotencyKey);
  }

  public Optional<IdempotencyKey> findExisting(String key, String scope) {
    return idempotencyKeyRepository.findByIdempotencyKeyAndScope(key, scope);
  }

  public boolean isLeaseExpired(IdempotencyKey idempotencyKey) {
    return ZonedDateTime.now().isAfter(idempotencyKey.getLeaseExpiresAt());
  }

  /**
   * Takes over an IN_PROGRESS key whose lease has expired (the owning request presumably crashed).
   * Uses the entity's {@code @Version} column as a compare-and-swap: if two retries race to steal
   * the same stale lease, the loser's flush throws ObjectOptimisticLockingFailureException. Returns
   * null if the key is no longer eligible to be stolen (already reclaimed, or lease no longer
   * expired).
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public IdempotencyKey stealExpiredLease(UUID id) {
    IdempotencyKey fresh = idempotencyKeyRepository.findById(id).orElseThrow();
    if (fresh.getStatus() != IdempotencyStatus.IN_PROGRESS || !isLeaseExpired(fresh)) {
      return null;
    }
    fresh.setLeaseExpiresAt(ZonedDateTime.now().plus(LEASE_DURATION));
    return idempotencyKeyRepository.saveAndFlush(fresh);
  }

  /**
   * Re-opens a COMPLETED key so its batch's still-retryable items can be attempted again. Same
   * compare-and-swap guard as {@link #stealExpiredLease} in case two identical retries race.
   * Returns null if the key is no longer COMPLETED (already reopened by a concurrent retry).
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public IdempotencyKey reopenCompleted(UUID id) {
    IdempotencyKey fresh = idempotencyKeyRepository.findById(id).orElseThrow();
    if (fresh.getStatus() != IdempotencyStatus.COMPLETED) {
      return null;
    }
    fresh.setStatus(IdempotencyStatus.IN_PROGRESS);
    fresh.setLeaseExpiresAt(ZonedDateTime.now().plus(LEASE_DURATION));
    return idempotencyKeyRepository.saveAndFlush(fresh);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void complete(UUID id, int responseStatus, String responseBody, UUID resourceId) {
    IdempotencyKey fresh = idempotencyKeyRepository.findById(id).orElseThrow();
    fresh.setStatus(IdempotencyStatus.COMPLETED);
    fresh.setResponseStatus(responseStatus);
    fresh.setResponseBody(responseBody);
    fresh.setResourceId(resourceId);
    idempotencyKeyRepository.saveAndFlush(fresh);
  }
}
