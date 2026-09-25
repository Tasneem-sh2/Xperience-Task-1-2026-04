package com.xperience.hero.service;

import com.xperience.hero.domain.AttendanceStatus;
import com.xperience.hero.domain.Event;
import com.xperience.hero.domain.EventStatus;
import com.xperience.hero.domain.Invitee;
import com.xperience.hero.domain.RsvpResponse;
import com.xperience.hero.exception.DuplicateInvitationException;
import com.xperience.hero.exception.EventNotFoundException;
import com.xperience.hero.exception.InvalidEventStateTransitionException;
import com.xperience.hero.exception.InviteeNotFoundException;
import com.xperience.hero.exception.RsvpWindowClosedException;
import com.xperience.hero.repository.EventRepository;
import com.xperience.hero.repository.InviteeRepository;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RsvpService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int RSVP_TOKEN_BYTES = 32;
    private static final int MAX_TOKEN_GENERATION_ATTEMPTS = 5;

    private final EventRepository eventRepository;
    private final InviteeRepository inviteeRepository;
    private final EventService eventService;

    public RsvpService(EventRepository eventRepository, InviteeRepository inviteeRepository, EventService eventService) {
        this.eventRepository = eventRepository;
        this.inviteeRepository = inviteeRepository;
        this.eventService = eventService;
    }

    @Transactional
    public Invitee createInvitation(Long eventId, String hostToken, String email) {
        Event event = eventService.findEventForHost(eventId, hostToken)
                .orElseThrow(() -> new EventNotFoundException("Event not found: " + eventId));

        if (event.getStatus() != EventStatus.OPEN) {
            throw new InvalidEventStateTransitionException(
                    "Cannot invite to a " + event.getStatus() + " event");
        }

        if (inviteeRepository.existsByEvent_IdAndEmail(eventId, email)) {
            throw new DuplicateInvitationException(
                    "Email already invited to this event: " + email);
        }

        Invitee invitee = new Invitee();
        invitee.setEvent(event);
        invitee.setEmail(email);
        invitee.setRsvpToken(generateUniqueRsvpToken());

        return inviteeRepository.save(invitee);
    }

    @Transactional(readOnly = true)
    public Invitee getInviteeByToken(String rsvpToken) {
        return inviteeRepository.findByRsvpToken(rsvpToken)
                .orElseThrow(() -> new InviteeNotFoundException("Invitee not found for token"));
    }

    @Transactional
    public Invitee submitRsvpResponse(String rsvpToken, RsvpResponse newResponse) {
        if (newResponse == null) {
            throw new IllegalArgumentException("response is required");
        }

        Invitee invitee = inviteeRepository.findByRsvpToken(rsvpToken)
                .orElseThrow(() -> new InviteeNotFoundException("Invitee not found for token"));

        if (invitee.getEvent() == null) {
            throw new EventNotFoundException("Invitee has no associated event");
        }
        Long eventId = invitee.getEvent().getId();

        // Lock the Event row for the rest of this transaction before making any
        // capacity-affecting decision (Step 12 / Phase 2 concurrency decision).
        Event event = eventRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> new EventNotFoundException("Event not found: " + eventId));

        if (event.getStatus() != EventStatus.OPEN) {
            throw new InvalidEventStateTransitionException(
                    "RSVP not allowed: event is " + event.getStatus());
        }
        if (!Instant.now().isBefore(event.getStartTime())) {
            throw new RsvpWindowClosedException("RSVP not allowed: event has already started");
        }

        boolean wasConfirmed = invitee.getAttendanceStatus() == AttendanceStatus.CONFIRMED;

        switch (newResponse) {
            case YES -> applyYes(invitee, event);
            case NO -> applyNo(invitee);
            case MAYBE -> applyMaybe(invitee);
        }

        boolean isConfirmedNow = invitee.getAttendanceStatus() == AttendanceStatus.CONFIRMED;

        // A confirmed slot was freed only if the invitee was CONFIRMED before this
        // change and is not CONFIRMED after it (covers YES->NO and YES->MAYBE).
        if (wasConfirmed && !isConfirmedNow) {
            promoteEarliestWaitlisted(event);
        }

        return invitee;
    }

    private void applyYes(Invitee invitee, Event event) {
        AttendanceStatus previousStatus = invitee.getAttendanceStatus();
        invitee.setResponse(RsvpResponse.YES);

        if (previousStatus == AttendanceStatus.CONFIRMED) {
            // Already occupying a confirmed slot; re-submitting YES changes nothing
            // and must not re-count or duplicate capacity.
            return;
        }

        Integer maxCapacity = event.getMaxCapacity();
        if (maxCapacity == null) {
            invitee.setAttendanceStatus(AttendanceStatus.CONFIRMED);
            invitee.setWaitlistedAt(null);
            return;
        }

        long confirmedCount = inviteeRepository.countByEvent_IdAndAttendanceStatus(
                event.getId(), AttendanceStatus.CONFIRMED);

        if (confirmedCount < maxCapacity) {
            invitee.setAttendanceStatus(AttendanceStatus.CONFIRMED);
            invitee.setWaitlistedAt(null);
        } else if (previousStatus == AttendanceStatus.WAITLISTED) {
            // Still full; remains WAITLISTED, keep original FIFO position.
            invitee.setAttendanceStatus(AttendanceStatus.WAITLISTED);
        } else {
            // Newly entering the waitlist.
            invitee.setAttendanceStatus(AttendanceStatus.WAITLISTED);
            invitee.setWaitlistedAt(Instant.now());
        }
    }

    private void applyNo(Invitee invitee) {
        invitee.setResponse(RsvpResponse.NO);
        invitee.setAttendanceStatus(null);
        invitee.setWaitlistedAt(null);
    }

    private void applyMaybe(Invitee invitee) {
        invitee.setResponse(RsvpResponse.MAYBE);
        invitee.setAttendanceStatus(null);
        invitee.setWaitlistedAt(null);
    }

    private void promoteEarliestWaitlisted(Event event) {
        Integer maxCapacity = event.getMaxCapacity();
        if (maxCapacity == null) {
            return; // no capacity limit => no waitlist concept
        }

        long confirmedCount = inviteeRepository.countByEvent_IdAndAttendanceStatus(
                event.getId(), AttendanceStatus.CONFIRMED);
        if (confirmedCount >= maxCapacity) {
            return; // defensive guard: no room actually available
        }

        inviteeRepository
                .findFirstByEvent_IdAndAttendanceStatusOrderByWaitlistedAtAsc(
                        event.getId(), AttendanceStatus.WAITLISTED)
                .ifPresent(candidate -> {
                    candidate.setAttendanceStatus(AttendanceStatus.CONFIRMED);
                    candidate.setWaitlistedAt(null);
                    // response is already YES for any waitlisted invitee.
                });
    }

    private String generateUniqueRsvpToken() {
        for (int attempt = 0; attempt < MAX_TOKEN_GENERATION_ATTEMPTS; attempt++) {
            String candidate = generateSecureToken();
            if (inviteeRepository.findByRsvpToken(candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new IllegalStateException(
                "Unable to generate a unique RSVP token after " + MAX_TOKEN_GENERATION_ATTEMPTS + " attempts");
    }

    private String generateSecureToken() {
        byte[] bytes = new byte[RSVP_TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
