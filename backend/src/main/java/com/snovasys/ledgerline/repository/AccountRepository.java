
package com.snovasys.ledgerline.repository;

import com.snovasys.ledgerline.entity.Account;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, Long> {
	List<Account> findByOwner_IdOrderByCreatedAtDesc(Long ownerId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select account from Account account where account.id = :id")
	Optional<Account> findByIdForUpdate(@Param("id") Long id);
}
