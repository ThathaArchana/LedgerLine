package com.snovasys.ledgerline.repository;

import com.snovasys.ledgerline.entity.LedgerEntry;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {
    List<LedgerEntry> findByTransaction_IdOrderById(Long transactionId);
}