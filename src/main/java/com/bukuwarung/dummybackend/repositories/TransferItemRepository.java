package com.bukuwarung.dummybackend.repositories;

import com.bukuwarung.dummybackend.domain.entities.TransferItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransferItemRepository extends JpaRepository<TransferItem, UUID> {

  List<TransferItem> findByBatch_IdOrderByItemIndex(UUID batchId);
}
