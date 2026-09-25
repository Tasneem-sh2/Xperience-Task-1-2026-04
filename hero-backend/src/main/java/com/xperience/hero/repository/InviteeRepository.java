package com.xperience.hero.repository;

import com.xperience.hero.domain.AttendanceStatus;
import com.xperience.hero.domain.Invitee;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InviteeRepository extends JpaRepository<Invitee, Long> {

    Optional<Invitee> findByRsvpToken(String rsvpToken);

    boolean existsByEvent_IdAndEmail(Long eventId, String email);

    long countByEvent_IdAndAttendanceStatus(Long eventId, AttendanceStatus attendanceStatus);

    Optional<Invitee> findFirstByEvent_IdAndAttendanceStatusOrderByWaitlistedAtAsc(
            Long eventId, AttendanceStatus attendanceStatus);
}
