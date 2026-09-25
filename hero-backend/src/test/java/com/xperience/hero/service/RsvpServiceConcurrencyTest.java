package com.xperience.hero.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.xperience.hero.domain.AttendanceStatus;
import com.xperience.hero.domain.Event;
import com.xperience.hero.domain.Invitee;
import com.xperience.hero.domain.RsvpResponse;
import com.xperience.hero.repository.EventRepository;
import com.xperience.hero.repository.InviteeRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Real, multi-threaded integration test exercising the pessimistic Event-row
 * lock. This deliberately does NOT run inside a single test-level transaction:
 * each of the two concurrent RSVP submissions must obtain its own database
 * connection/transaction so they can genuinely contend for the same row lock.
 * Wrapping this test in @Transactional (Spring's usual test-rollback pattern)
 * would make both calls share one connection, making real lock contention
 * impossible to exercise.
 */
@SpringBootTest
class RsvpServiceConcurrencyTest {

    @Autowired
    private EventService eventService;

    @Autowired
    private RsvpService rsvpService;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private InviteeRepository inviteeRepository;

    private Long createdEventId;

    @AfterEach
    void cleanUp() {
        if (createdEventId != null) {
            inviteeRepository.findAll().stream()
                    .filter(invitee -> invitee.getEvent() != null && createdEventId.equals(invitee.getEvent().getId()))
                    .forEach(invitee -> inviteeRepository.deleteById(invitee.getId()));
            eventRepository.deleteById(createdEventId);
            createdEventId = null;
        }
    }

    @Test
    void twoSimultaneousYesResponses_forLastSpot_resolveToExactlyOneConfirmedAndOneWaitlisted() throws Exception {
        Event event = eventService.createEvent(
                "Concurrency Test Event",
                "capacity = 1",
                Instant.now().plus(7, ChronoUnit.DAYS),
                "HQ",
                1);
        createdEventId = event.getId();
        String hostToken = event.getHostToken();

        Invitee invitee1 = rsvpService.createInvitation(createdEventId, hostToken, "racer1@example.com");
        Invitee invitee2 = rsvpService.createInvitation(createdEventId, hostToken, "racer2@example.com");
        String token1 = invitee1.getRsvpToken();
        String token2 = invitee2.getRsvpToken();

        // Both threads block on this barrier immediately before calling the
        // transactional service method, so they attempt to acquire the Event
        // row's pessimistic lock as close to simultaneously as possible.
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Callable<Invitee> submitYes1 = () -> {
            barrier.await(10, TimeUnit.SECONDS);
            return rsvpService.submitRsvpResponse(token1, RsvpResponse.YES);
        };
        Callable<Invitee> submitYes2 = () -> {
            barrier.await(10, TimeUnit.SECONDS);
            return rsvpService.submitRsvpResponse(token2, RsvpResponse.YES);
        };

        List<Future<Invitee>> futures = executor.invokeAll(List.of(submitYes1, submitYes2), 30, TimeUnit.SECONDS);
        executor.shutdown();

        // Both calls must complete without throwing - neither request should
        // be rejected; one must be CONFIRMED and the other WAITLISTED.
        for (Future<Invitee> future : futures) {
            future.get();
        }

        // Re-read final state fresh from the database, independent of the
        // in-memory objects returned by the two threads.
        Invitee finalInvitee1 = inviteeRepository.findByRsvpToken(token1).orElseThrow();
        Invitee finalInvitee2 = inviteeRepository.findByRsvpToken(token2).orElseThrow();

        assertThat(List.of(finalInvitee1.getAttendanceStatus(), finalInvitee2.getAttendanceStatus()))
                .containsExactlyInAnyOrder(AttendanceStatus.CONFIRMED, AttendanceStatus.WAITLISTED);

        long confirmedCount = inviteeRepository.countByEvent_IdAndAttendanceStatus(
                createdEventId, AttendanceStatus.CONFIRMED);
        assertThat(confirmedCount).isEqualTo(1);
    }
}
