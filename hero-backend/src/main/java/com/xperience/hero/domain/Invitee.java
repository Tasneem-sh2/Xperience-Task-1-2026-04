package com.xperience.hero.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "invitees",
        uniqueConstraints = @UniqueConstraint(columnNames = {"event_id", "email"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Invitee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(nullable = false)
    private String email;

    @Column(name = "rsvp_token", nullable = false, unique = true)
    private String rsvpToken;

    @Enumerated(EnumType.STRING)
    private RsvpResponse response;

    @Enumerated(EnumType.STRING)
    private AttendanceStatus attendanceStatus;

    private Instant waitlistedAt;
}
