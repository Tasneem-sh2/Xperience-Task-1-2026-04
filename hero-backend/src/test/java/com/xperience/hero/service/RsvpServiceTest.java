package com.xperience.hero.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.xperience.hero.domain.AttendanceStatus;
import com.xperience.hero.domain.Event;
import com.xperience.hero.domain.EventStatus;
import com.xperience.hero.domain.Invitee;
import com.xperience.hero.domain.RsvpResponse;
import com.xperience.hero.exception.DuplicateInvitationException;
import com.xperience.hero.exception.InvalidEventStateTransitionException;
import com.xperience.hero.exception.RsvpWindowClosedException;
import com.xperience.hero.repository.EventRepository;
import com.xperience.hero.repository.InviteeRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RsvpServiceTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private InviteeRepository inviteeRepository;

    @Mock
    private EventService eventService;

    @InjectMocks
    private RsvpService rsvpService;

    private static final Instant FUTURE_START = Instant.now().plus(7, ChronoUnit.DAYS);
    private static final Instant PAST_START = Instant.now().minus(1, ChronoUnit.DAYS);

    // ---------- Invitation creation ----------

    @Test
    void invitation_succeedsForOpenEvent() {
        Event event = openEvent(1L, null);
        when(eventService.findEventForHost(1L, "host-token")).thenReturn(Optional.of(event));
        when(inviteeRepository.existsByEvent_IdAndEmail(1L, "a@example.com")).thenReturn(false);
        when(inviteeRepository.findByRsvpToken(any())).thenReturn(Optional.empty());
        when(inviteeRepository.save(any(Invitee.class))).thenAnswer(inv -> inv.getArgument(0));

        Invitee created = rsvpService.createInvitation(1L, "host-token", "a@example.com");

        assertThat(created.getEmail()).isEqualTo("a@example.com");
        assertThat(created.getEvent()).isSameAs(event);
        assertThat(created.getResponse()).isNull();
        assertThat(created.getAttendanceStatus()).isNull();
        assertThat(created.getWaitlistedAt()).isNull();
    }

    @Test
    void invitation_generatesRsvpToken() {
        Event event = openEvent(1L, null);
        when(eventService.findEventForHost(1L, "host-token")).thenReturn(Optional.of(event));
        when(inviteeRepository.existsByEvent_IdAndEmail(1L, "a@example.com")).thenReturn(false);
        when(inviteeRepository.findByRsvpToken(any())).thenReturn(Optional.empty());
        when(inviteeRepository.save(any(Invitee.class))).thenAnswer(inv -> inv.getArgument(0));

        Invitee created = rsvpService.createInvitation(1L, "host-token", "a@example.com");

        assertThat(created.getRsvpToken()).isNotBlank();
    }

    @Test
    void invitation_rejectsDuplicateEmail() {
        Event event = openEvent(1L, null);
        when(eventService.findEventForHost(1L, "host-token")).thenReturn(Optional.of(event));
        when(inviteeRepository.existsByEvent_IdAndEmail(1L, "a@example.com")).thenReturn(true);

        assertThatThrownBy(() -> rsvpService.createInvitation(1L, "host-token", "a@example.com"))
                .isInstanceOf(DuplicateInvitationException.class);
    }

    @Test
    void invitation_rejectedForClosedEvent() {
        Event event = openEvent(1L, null);
        event.setStatus(EventStatus.CLOSED);
        when(eventService.findEventForHost(1L, "host-token")).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> rsvpService.createInvitation(1L, "host-token", "a@example.com"))
                .isInstanceOf(InvalidEventStateTransitionException.class);
    }

    @Test
    void invitation_rejectedForCancelledEvent() {
        Event event = openEvent(1L, null);
        event.setStatus(EventStatus.CANCELLED);
        when(eventService.findEventForHost(1L, "host-token")).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> rsvpService.createInvitation(1L, "host-token", "a@example.com"))
                .isInstanceOf(InvalidEventStateTransitionException.class);
    }

    // ---------- YES + capacity ----------

    @Test
    void yes_unlimitedCapacity_becomesConfirmed() {
        Event event = openEvent(1L, null);
        Invitee invitee = pendingInvitee(event);
        when(inviteeRepository.findByRsvpToken("token")).thenReturn(Optional.of(invitee));
        when(eventRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(event));

        Invitee result = rsvpService.submitRsvpResponse("token", RsvpResponse.YES);

        assertThat(result.getResponse()).isEqualTo(RsvpResponse.YES);
        assertThat(result.getAttendanceStatus()).isEqualTo(AttendanceStatus.CONFIRMED);
        assertThat(result.getWaitlistedAt()).isNull();
    }

    @Test
    void yes_availableCapacity_becomesConfirmed() {
        Event event = openEvent(1L, 5);
        Invitee invitee = pendingInvitee(event);
        when(inviteeRepository.findByRsvpToken("token")).thenReturn(Optional.of(invitee));
        when(eventRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(event));
        when(inviteeRepository.countByEvent_IdAndAttendanceStatus(1L, AttendanceStatus.CONFIRMED)).thenReturn(2L);

        Invitee result = rsvpService.submitRsvpResponse("token", RsvpResponse.YES);

        assertThat(result.getAttendanceStatus()).isEqualTo(AttendanceStatus.CONFIRMED);
    }

    @Test
    void yes_fullCapacity_becomesWaitlisted() {
        Event event = openEvent(1L, 1);
        Invitee invitee = pendingInvitee(event);
        when(inviteeRepository.findByRsvpToken("token")).thenReturn(Optional.of(invitee));
        when(eventRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(event));
        when(inviteeRepository.countByEvent_IdAndAttendanceStatus(1L, AttendanceStatus.CONFIRMED)).thenReturn(1L);

        Invitee result = rsvpService.submitRsvpResponse("token", RsvpResponse.YES);

        assertThat(result.getResponse()).isEqualTo(RsvpResponse.YES);
        assertThat(result.getAttendanceStatus()).isEqualTo(AttendanceStatus.WAITLISTED);
        assertThat(result.getWaitlistedAt()).isNotNull();
    }

    // ---------- NO / MAYBE ----------

    @Test
    void no_clearsAttendanceStatus() {
        Event event = openEvent(1L, 1);
        Invitee invitee = confirmedInvitee(event);
        when(inviteeRepository.findByRsvpToken("token")).thenReturn(Optional.of(invitee));
        when(eventRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(event));
        when(inviteeRepository.countByEvent_IdAndAttendanceStatus(1L, AttendanceStatus.CONFIRMED)).thenReturn(0L);
        when(inviteeRepository.findFirstByEvent_IdAndAttendanceStatusOrderByWaitlistedAtAsc(1L, AttendanceStatus.WAITLISTED))
                .thenReturn(Optional.empty());

        Invitee result = rsvpService.submitRsvpResponse("token", RsvpResponse.NO);

        assertThat(result.getResponse()).isEqualTo(RsvpResponse.NO);
        assertThat(result.getAttendanceStatus()).isNull();
        assertThat(result.getWaitlistedAt()).isNull();
    }

    @Test
    void maybe_clearsAttendanceStatus() {
        Event event = openEvent(1L, null);
        Invitee invitee = pendingInvitee(event);
        when(inviteeRepository.findByRsvpToken("token")).thenReturn(Optional.of(invitee));
        when(eventRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(event));

        Invitee result = rsvpService.submitRsvpResponse("token", RsvpResponse.MAYBE);

        assertThat(result.getResponse()).isEqualTo(RsvpResponse.MAYBE);
        assertThat(result.getAttendanceStatus()).isNull();
        assertThat(result.getWaitlistedAt()).isNull();
    }

    // ---------- Promotion ----------

    @Test
    void confirmedYesToNo_promotesEarliestWaitlisted() {
        Event event = openEvent(1L, 1);
        Invitee invitee = confirmedInvitee(event);
        Invitee waitlisted = waitlistedInvitee(event, Instant.now().minus(1, ChronoUnit.HOURS));

        when(inviteeRepository.findByRsvpToken("token")).thenReturn(Optional.of(invitee));
        when(eventRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(event));
        when(inviteeRepository.countByEvent_IdAndAttendanceStatus(1L, AttendanceStatus.CONFIRMED)).thenReturn(0L);
        when(inviteeRepository.findFirstByEvent_IdAndAttendanceStatusOrderByWaitlistedAtAsc(1L, AttendanceStatus.WAITLISTED))
                .thenReturn(Optional.of(waitlisted));

        rsvpService.submitRsvpResponse("token", RsvpResponse.NO);

        assertThat(waitlisted.getAttendanceStatus()).isEqualTo(AttendanceStatus.CONFIRMED);
        assertThat(waitlisted.getResponse()).isEqualTo(RsvpResponse.YES);
        assertThat(waitlisted.getWaitlistedAt()).isNull();
    }

    @Test
    void confirmedYesToMaybe_promotesEarliestWaitlisted() {
        Event event = openEvent(1L, 1);
        Invitee invitee = confirmedInvitee(event);
        Invitee waitlisted = waitlistedInvitee(event, Instant.now().minus(1, ChronoUnit.HOURS));

        when(inviteeRepository.findByRsvpToken("token")).thenReturn(Optional.of(invitee));
        when(eventRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(event));
        when(inviteeRepository.countByEvent_IdAndAttendanceStatus(1L, AttendanceStatus.CONFIRMED)).thenReturn(0L);
        when(inviteeRepository.findFirstByEvent_IdAndAttendanceStatusOrderByWaitlistedAtAsc(1L, AttendanceStatus.WAITLISTED))
                .thenReturn(Optional.of(waitlisted));

        rsvpService.submitRsvpResponse("token", RsvpResponse.MAYBE);

        assertThat(waitlisted.getAttendanceStatus()).isEqualTo(AttendanceStatus.CONFIRMED);
        assertThat(waitlisted.getWaitlistedAt()).isNull();
    }

    @Test
    void waitlistedYesToNo_removesWaitlistPosition_withoutPromotion() {
        Event event = openEvent(1L, 1);
        Invitee invitee = waitlistedInvitee(event, Instant.now());
        when(inviteeRepository.findByRsvpToken("token")).thenReturn(Optional.of(invitee));
        when(eventRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(event));

        Invitee result = rsvpService.submitRsvpResponse("token", RsvpResponse.NO);

        assertThat(result.getResponse()).isEqualTo(RsvpResponse.NO);
        assertThat(result.getAttendanceStatus()).isNull();
        assertThat(result.getWaitlistedAt()).isNull();
        verify(inviteeRepository, never())
                .findFirstByEvent_IdAndAttendanceStatusOrderByWaitlistedAtAsc(any(), any());
    }

    @Test
    void waitlistedYesToMaybe_removesWaitlistPosition_withoutPromotion() {
        Event event = openEvent(1L, 1);
        Invitee invitee = waitlistedInvitee(event, Instant.now());
        when(inviteeRepository.findByRsvpToken("token")).thenReturn(Optional.of(invitee));
        when(eventRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(event));

        Invitee result = rsvpService.submitRsvpResponse("token", RsvpResponse.MAYBE);

        assertThat(result.getAttendanceStatus()).isNull();
        assertThat(result.getWaitlistedAt()).isNull();
        verify(inviteeRepository, never())
                .findFirstByEvent_IdAndAttendanceStatusOrderByWaitlistedAtAsc(any(), any());
    }

    @Test
    void fifoWaitlistPromotion_usesEarliestWaitlistedQuery() {
        Event event = openEvent(1L, 1);
        Invitee invitee = confirmedInvitee(event);
        Invitee earliestWaitlisted = waitlistedInvitee(event, Instant.now().minus(2, ChronoUnit.HOURS));

        when(inviteeRepository.findByRsvpToken("token")).thenReturn(Optional.of(invitee));
        when(eventRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(event));
        when(inviteeRepository.countByEvent_IdAndAttendanceStatus(1L, AttendanceStatus.CONFIRMED)).thenReturn(0L);
        when(inviteeRepository.findFirstByEvent_IdAndAttendanceStatusOrderByWaitlistedAtAsc(1L, AttendanceStatus.WAITLISTED))
                .thenReturn(Optional.of(earliestWaitlisted));

        rsvpService.submitRsvpResponse("token", RsvpResponse.NO);

        verify(inviteeRepository)
                .findFirstByEvent_IdAndAttendanceStatusOrderByWaitlistedAtAsc(eq(1L), eq(AttendanceStatus.WAITLISTED));
        assertThat(earliestWaitlisted.getAttendanceStatus()).isEqualTo(AttendanceStatus.CONFIRMED);
    }

    @Test
    void promotion_neverRunsWhenNoCapacityIsActuallyAvailable() {
        // Defensive guard: even if promotion is triggered, it must not proceed
        // if the confirmed count is already at (or above) capacity.
        Event event = openEvent(1L, 1);
        Invitee invitee = confirmedInvitee(event);
        when(inviteeRepository.findByRsvpToken("token")).thenReturn(Optional.of(invitee));
        when(eventRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(event));
        // Simulate an inconsistent read where capacity still looks full.
        when(inviteeRepository.countByEvent_IdAndAttendanceStatus(1L, AttendanceStatus.CONFIRMED)).thenReturn(1L);

        rsvpService.submitRsvpResponse("token", RsvpResponse.NO);

        verify(inviteeRepository, never())
                .findFirstByEvent_IdAndAttendanceStatusOrderByWaitlistedAtAsc(any(), any());
    }

    @Test
    void repeatedChanges_doNotCorruptCapacityState() {
        Event event = openEvent(1L, 1);
        Invitee invitee = pendingInvitee(event);
        when(inviteeRepository.findByRsvpToken("token")).thenReturn(Optional.of(invitee));
        when(eventRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(event));

        // NO -> YES (confirmed, capacity available)
        when(inviteeRepository.countByEvent_IdAndAttendanceStatus(1L, AttendanceStatus.CONFIRMED)).thenReturn(0L);
        rsvpService.submitRsvpResponse("token", RsvpResponse.YES);
        assertInvariants(invitee);
        assertThat(invitee.getAttendanceStatus()).isEqualTo(AttendanceStatus.CONFIRMED);

        // YES/CONFIRMED -> YES again (no-op, no capacity re-check should be needed to stay CONFIRMED)
        rsvpService.submitRsvpResponse("token", RsvpResponse.YES);
        assertInvariants(invitee);
        assertThat(invitee.getAttendanceStatus()).isEqualTo(AttendanceStatus.CONFIRMED);

        // YES/CONFIRMED -> MAYBE (frees the slot; no one waitlisted to promote)
        when(inviteeRepository.findFirstByEvent_IdAndAttendanceStatusOrderByWaitlistedAtAsc(1L, AttendanceStatus.WAITLISTED))
                .thenReturn(Optional.empty());
        rsvpService.submitRsvpResponse("token", RsvpResponse.MAYBE);
        assertInvariants(invitee);
        assertThat(invitee.getResponse()).isEqualTo(RsvpResponse.MAYBE);

        // MAYBE -> NO
        rsvpService.submitRsvpResponse("token", RsvpResponse.NO);
        assertInvariants(invitee);
        assertThat(invitee.getResponse()).isEqualTo(RsvpResponse.NO);
    }

    private void assertInvariants(Invitee invitee) {
        if (invitee.getResponse() != RsvpResponse.YES) {
            assertThat(invitee.getAttendanceStatus()).isNull();
            assertThat(invitee.getWaitlistedAt()).isNull();
        }
        if (invitee.getAttendanceStatus() != AttendanceStatus.WAITLISTED) {
            assertThat(invitee.getWaitlistedAt()).isNull();
        }
    }

    // ---------- Event state / time guards ----------

    @Test
    void rsvpChange_rejectedAfterEventStart() {
        Event event = openEvent(1L, null);
        event.setStartTime(PAST_START);
        Invitee invitee = pendingInvitee(event);
        when(inviteeRepository.findByRsvpToken("token")).thenReturn(Optional.of(invitee));
        when(eventRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> rsvpService.submitRsvpResponse("token", RsvpResponse.YES))
                .isInstanceOf(RsvpWindowClosedException.class);
    }

    @Test
    void rsvpChange_rejectedForClosedEvent() {
        Event event = openEvent(1L, null);
        event.setStatus(EventStatus.CLOSED);
        Invitee invitee = pendingInvitee(event);
        when(inviteeRepository.findByRsvpToken("token")).thenReturn(Optional.of(invitee));
        when(eventRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> rsvpService.submitRsvpResponse("token", RsvpResponse.YES))
                .isInstanceOf(InvalidEventStateTransitionException.class);
    }

    @Test
    void rsvpChange_rejectedForCancelledEvent() {
        Event event = openEvent(1L, null);
        event.setStatus(EventStatus.CANCELLED);
        Invitee invitee = pendingInvitee(event);
        when(inviteeRepository.findByRsvpToken("token")).thenReturn(Optional.of(invitee));
        when(eventRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> rsvpService.submitRsvpResponse("token", RsvpResponse.YES))
                .isInstanceOf(InvalidEventStateTransitionException.class);
    }

    // ---------- fixtures ----------

    private Event openEvent(Long id, Integer maxCapacity) {
        Event event = new Event();
        event.setId(id);
        event.setTitle("Team Offsite");
        event.setDescription("desc");
        event.setStartTime(FUTURE_START);
        event.setLocation("HQ");
        event.setMaxCapacity(maxCapacity);
        event.setStatus(EventStatus.OPEN);
        event.setHostToken("host-token");
        return event;
    }

    private Invitee pendingInvitee(Event event) {
        Invitee invitee = new Invitee();
        invitee.setId(1L);
        invitee.setEvent(event);
        invitee.setEmail("a@example.com");
        invitee.setRsvpToken("token");
        return invitee;
    }

    private Invitee confirmedInvitee(Event event) {
        Invitee invitee = pendingInvitee(event);
        invitee.setResponse(RsvpResponse.YES);
        invitee.setAttendanceStatus(AttendanceStatus.CONFIRMED);
        return invitee;
    }

    private Invitee waitlistedInvitee(Event event, Instant waitlistedAt) {
        Invitee invitee = new Invitee();
        invitee.setId(2L);
        invitee.setEvent(event);
        invitee.setEmail("b@example.com");
        invitee.setRsvpToken("token-2");
        invitee.setResponse(RsvpResponse.YES);
        invitee.setAttendanceStatus(AttendanceStatus.WAITLISTED);
        invitee.setWaitlistedAt(waitlistedAt);
        return invitee;
    }
}
