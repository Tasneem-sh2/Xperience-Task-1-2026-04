package com.xperience.hero.repository;

import com.xperience.hero.domain.Event;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventRepository extends JpaRepository<Event, Long> {

    /**
     * Locks the Event row for the duration of the enclosing transaction.
     * Intended for the pessimistic-locking concurrency approach decided for
     * the RSVP service (not yet implemented).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Event e where e.id = :id")
    Optional<Event> findByIdForUpdate(@Param("id") Long id);

    boolean existsByHostToken(String hostToken);
}
