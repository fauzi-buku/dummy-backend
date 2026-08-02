package com.bukuwarung.dummybackend.repositories;

import com.bukuwarung.dummybackend.domain.entities.IdempotencyKey;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, UUID> {

  Optional<IdempotencyKey> findByIdempotencyKeyAndScope(String idempotencyKey, String scope);
}
