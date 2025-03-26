package org.example.springlock.pessimistic;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PessimisticCounterRepository extends JpaRepository<PessimisticCounter, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from PessimisticCounter c where c.id = :id")
    Optional<PessimisticCounter> findByIdWithPessimisticLock(@Param("id") Long id);
}
