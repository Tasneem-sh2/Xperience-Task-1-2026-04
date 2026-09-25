package com.xperience.hero.service;

import com.xperience.hero.domain.Event;
import com.xperience.hero.domain.EventStatus;
import com.xperience.hero.exception.EventNotFoundException;
import com.xperience.hero.exception.InvalidEventException;
import com.xperience.hero.exception.InvalidEventStateTransitionException;
import com.xperience.hero.repository.EventRepository;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int HOST_TOKEN_BYTES = 32;
    private static final int MAX_TOKEN_GENERATION_ATTEMPTS = 5;

    private final EventRepository eventRepository;

    public EventService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @Transactional
    public Event createEvent(String title, String description, Instant startTime, String location, Integer maxCapacity) {
        validateCreate(title, startTime, location, maxCapacity);

        Event event = new Event();
        event.setTitle(title);
        event.setDescription(description);
        event.setStartTime(startTime);
        event.setLocation(location);
        event.setMaxCapacity(maxCapacity);
        event.setStatus(EventStatus.OPEN);
        event.setHostToken(generateUniqueHostToken());

        return eventRepository.save(event);
    }

    @Transactional(readOnly = true)
    public Optional<Event> findEventForHost(Long eventId, String hostToken) {
        if (eventId == null || hostToken == null) {
            return Optional.empty();
        }
        return eventRepository.findById(eventId)
                .filter(event -> hostToken.equals(event.getHostToken()));
    }

    @Transactional
    public Event closeEvent(Long eventId) {
        Event event = getEventOrThrow(eventId);
        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new InvalidEventStateTransitionException("Cannot close a cancelled event");
        }
        if (event.getStatus() == EventStatus.OPEN) {
            event.setStatus(EventStatus.CLOSED);
        }
        return event;
    }

    @Transactional
    public Event cancelEvent(Long eventId) {
        Event event = getEventOrThrow(eventId);
        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new InvalidEventStateTransitionException("Event is already cancelled");
        }
        event.setStatus(EventStatus.CANCELLED);
        return event;
    }

    private Event getEventOrThrow(Long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException("Event not found: " + eventId));
    }

    private void validateCreate(String title, Instant startTime, String location, Integer maxCapacity) {
        if (title == null || title.isBlank()) {
            throw new InvalidEventException("title is required");
        }
        if (startTime == null) {
            throw new InvalidEventException("startTime is required");
        }
        if (location == null || location.isBlank()) {
            throw new InvalidEventException("location is required");
        }
        if (maxCapacity != null && maxCapacity <= 0) {
            throw new InvalidEventException("maxCapacity must be a positive value");
        }
    }

    private String generateUniqueHostToken() {
        for (int attempt = 0; attempt < MAX_TOKEN_GENERATION_ATTEMPTS; attempt++) {
            String candidate = generateSecureToken();
            if (!eventRepository.existsByHostToken(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException(
                "Unable to generate a unique host token after " + MAX_TOKEN_GENERATION_ATTEMPTS + " attempts");
    }

    private String generateSecureToken() {
        byte[] bytes = new byte[HOST_TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
