
package com.snovasys.ledgerline.repository;

import com.snovasys.ledgerline.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByAccount_IdOrderByCreatedAtDesc(Long accountId);
    List<Transaction> findAllByOrderByCreatedAtDesc();

    java.util.Optional<Transaction> findByIdempotencyKey(String idempotencyKey);
}
