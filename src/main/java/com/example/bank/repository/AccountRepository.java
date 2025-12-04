package com.example.bank.repository;

import com.example.bank.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

/**
 * AccountRepository - Data access layer for Account entities.
 * Provides pessimistic locking to prevent concurrent modification issues.
 */
@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {

    /**
     * Finds an account by ID with pessimistic read lock (prevents concurrent modifications).
     * Useful for transactions where we need to read the current balance.
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("SELECT a FROM Account a WHERE a.id = :id")
    Optional<Account> findByIdWithReadLock(@Param("id") UUID id);

    /**
     * Finds an account by ID with pessimistic write lock (exclusive lock).
     * Used before performing write operations (debit/credit).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id = :id")
    Optional<Account> findByIdWithWriteLock(@Param("id") UUID id);

    /**
     * Finds accounts by holder name.
     */
    Optional<Account> findByHolderName(String holderName);
}
