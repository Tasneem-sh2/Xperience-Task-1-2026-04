package com.xperience.hero.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.xperience.hero.domain.Event;
import com.xperience.hero.domain.EventStatus;
import com.xperience.hero.exception.InvalidEventException;
import com.xperience.hero.exception.InvalidEventStateTransitionException;
import com.xperience.hero.repository.EventRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    private EventRepository eventRepository;

    @InjectMocks
    private EventService eventService;

    @Test
    void createEvent_succeeds_withValidData() {
        when(eventRepository.existsByHostToken(anyString())).thenReturn(false);
        // save(event) returns whatever entity it was given, as a real repository would.
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Event created = eventService.createEvent(
                "Team Offsite", "Annual planning day", Instant.parse("2027-01-10T09:00:00Z"), "HQ", 20);

        assertThat(created.getTitle()).isEqualTo("Team Offsite");
        assertThat(created.getDescription()).isEqualTo("Annual planning day");
        assertThat(created.getStartTime()).isEqualTo(Instant.parse("2027-01-10T09:00:00Z"));
        assertThat(created.getLocation()).isEqualTo("HQ");
        assertThat(created.getMaxCapacity()).isEqualTo(20);
        assertThat(created.getStatus()).isEqualTo(EventStatus.OPEN);
        assertThat(created.getHostToken()).isNotBlank();
    }

    @Test
    void createEvent_rejectsNonPositiveMaxCapacity() {
        assertThatThrownBy(() -> eventService.createEvent(
                        "Team Offsite", "desc", Instant.parse("2027-01-10T09:00:00Z"), "HQ", 0))
                .isInstanceOf(InvalidEventException.class);

        assertThatThrownBy(() -> eventService.createEvent(
                        "Team Offsite", "desc", Instant.parse("2027-01-10T09:00:00Z"), "HQ", -5))
                .isInstanceOf(InvalidEventException.class);
    }

    @Test
    void closeEvent_transitionsOpenToClosed() {
        Event event = openEvent();
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));

        Event result = eventService.closeEvent(1L);

        assertThat(result.getStatus()).isEqualTo(EventStatus.CLOSED);
    }

    @Test
    void cancelEvent_transitionsOpenToCancelled() {
        Event event = openEvent();
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));

        Event result = eventService.cancelEvent(1L);

        assertThat(result.getStatus()).isEqualTo(EventStatus.CANCELLED);
    }

    @Test
    void cancelEvent_transitionsClosedToCancelled() {
        Event event = openEvent();
        event.setStatus(EventStatus.CLOSED);
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));

        Event result = eventService.cancelEvent(1L);

        assertThat(result.getStatus()).isEqualTo(EventStatus.CANCELLED);
    }

    @Test
    void closeOrCancelEvent_rejectsTransitionFromCancelled() {
        Event event = openEvent();
        event.setStatus(EventStatus.CANCELLED);
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> eventService.closeEvent(1L))
                .isInstanceOf(InvalidEventStateTransitionException.class);
        assertThatThrownBy(() -> eventService.cancelEvent(1L))
                .isInstanceOf(InvalidEventStateTransitionException.class);
    }

    @Test
    void findEventForHost_succeedsWhenTokenMatches() {
        Event event = openEvent();
        event.setHostToken("correct-token");
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));

        Optional<Event> result = eventService.findEventForHost(1L, "correct-token");

        assertThat(result).isPresent();
        assertThat(result.get()).isSameAs(event);
    }

    @Test
    void findEventForHost_failsWhenTokenDoesNotMatch() {
        Event event = openEvent();
        event.setHostToken("correct-token");
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));

        Optional<Event> result = eventService.findEventForHost(1L, "wrong-token");

        assertThat(result).isEmpty();
    }

    private Event openEvent() {
        Event event = new Event();
        event.setId(1L);
        event.setTitle("Team Offsite");
        event.setDescription("desc");
        event.setStartTime(Instant.parse("2027-01-10T09:00:00Z"));
        event.setLocation("HQ");
        event.setMaxCapacity(20);
        event.setStatus(EventStatus.OPEN);
        event.setHostToken("initial-token");
        return event;
    }
}
