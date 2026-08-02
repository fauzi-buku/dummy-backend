package com.bukuwarung.dummybackend.repositories;

import com.bukuwarung.dummybackend.domain.entities.TransferBatch;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransferBatchRepository extends JpaRepository<TransferBatch, UUID> {}
