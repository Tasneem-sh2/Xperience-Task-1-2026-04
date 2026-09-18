## Step 01 — Setup

- **Feature**: Event RSVP Manager
- **Stack**: Java 17, Spring Boot 4 (Spring MVC, Spring Data JPA) · PostgreSQL · React 19 + TypeScript + Vite
- **Scaffold**: pre-provided full-stack repo (`hero-backend/`, `hero-frontend/`); Phase 1 is design-only, no code changes
- **AI partner**: Claude Code (VS Code), used to draft each step; all output is reviewed and edited by the author before being copied into this file
- **Process rule**: working discussion/back-and-forth with the AI stays out of this file — only the reviewed result of each step is recorded here

## Step 02 — Raw Feature Brief

A small web application where users can host events and collect RSVPs from invitees.

- A user can create an event with a title, description, date/time, location, and optional max-capacity.
- The creator becomes the **host** of that event.
- The host can invite people by email.
- Each invitee receives a unique link and can respond: **Yes / No / Maybe**.
- The host sees a live attendance dashboard with counts and a list of attendees.
- If the event has a max-capacity and it is reached, new "Yes" RSVPs go to a **waitlist**.
- A waitlisted attendee automatically moves to confirmed if a confirmed attendee changes their RSVP to No.
- The host can cancel the event or close it to further responses at any time.
- An invitee can change their RSVP at any point **before** the event starts.
- After the event start time, all RSVPs are locked.

## Step 03 — Problem Statement

Event hosts need a way to create an event, invite specific people, and maintain
an accurate, up-to-date view of expected attendance. Invitees need a simple way
to respond to an invitation and update their response before the event starts.

When an event has limited capacity, the host also needs attendance to be managed
without exceeding the available capacity, while still accounting for invitees
who may become confirmed when a spot becomes available. The system must also
provide clear control over when responses are allowed, including closing or
cancelling an event and locking RSVPs once the event has started.

## Step 04 — Goals and Non-Goals

### Goals

- Allow a user to create an event with a title, description, date/time, location, and optional max-capacity.
- Make the event creator the host of that event.
- Allow the host to invite people by email.
- Give each invitee a unique link to respond Yes / No / Maybe.
- Allow the invitee to change their RSVP at any point before the event starts.
- Provide the host with a live attendance dashboard showing counts and a list of attendees.
- Enforce max-capacity: once reached, new "Yes" responses go to a waitlist.
- Automatically promote a waitlisted invitee to confirmed when a confirmed attendee changes their RSVP to No.
- Allow the host to close the event to further responses or cancel it at any time.
- Lock all RSVPs once the event start time has passed.

### Non-Goals

- No invitee accounts, login, or authentication — access is via the unique invitation link.
- No public discovery or browsing of events.
- No payments, ticketing, or paid capacity management.
- No recurring or repeating events.
- No in-app messaging or communication between the host and invitees beyond the invitation and RSVP flow.
- No mechanism for invitees to invite additional people; the guest list is controlled by the host.

## Step 05 — Context and Constraints

- **Fixed technology stack**: Java 17, Spring Boot 4 (Spring MVC, Spring Data JPA) on the
  backend, PostgreSQL as the database, React 19 + TypeScript + Vite on the frontend —
  set by the provided scaffold, not open for reconsideration in this design.
- **Single host per event**: the brief only describes one creator/host per event; no
  co-hosting or transfer of host ownership is described.
- **Invitee access is link-based, not account-based**: invitees receive a unique link
  and respond through it — the brief does not describe invitee login or accounts.
- **Capacity is optional and per-event**: max-capacity is set (or not) individually
  when an event is created; there is no global or cross-event capacity concept.
- **Time is a first-class constraint**: the system must know the event's start time and
  compare it against the current time to enforce the RSVP lock — this is a hard
  boundary condition described directly in the brief, not an implementation detail.
- **Host retains lifecycle control**: the brief states the host can close an event to
  further responses or cancel it at any time.

## Step 06 — Facts, Assumptions, and Open Questions

### Facts

- A user can create an event with a title, description, date/time, location, and optional max-capacity.
- The event creator becomes the host.
- The host can invite people by email.
- Each invitee receives a unique link and can respond Yes / No / Maybe.
- The host sees a live attendance dashboard with counts and a list of attendees.
- When max-capacity is reached, new "Yes" responses go to a waitlist.
- A waitlisted attendee is automatically promoted when a confirmed attendee changes their RSVP to No.
- The host can close the event to further responses or cancel it at any time.
- An invitee can change their RSVP at any point before the event starts.
- After the event start time, all RSVPs are locked.
- The technology stack is fixed: Java 17, Spring Boot 4 with Spring MVC and Spring Data JPA, PostgreSQL, and React 19 with TypeScript and Vite.

### Assumptions

- Each invitation link identifies a single invitee and their RSVP.
- The waitlist has a defined ordering, although the specific ordering rule is not stated in the brief.
- Only confirmed "Yes" RSVPs count toward max-capacity; "Maybe" does not consume a confirmed capacity slot unless a later design decision states otherwise.
- When a confirmed attendee changes their RSVP to No, at most one waitlisted attendee is promoted for the newly available capacity.
- The system has an identifiable host/user concept because the brief refers to the event creator as the host, but the authentication and account model are not defined by the feature brief.

### Open Questions

- Does a "Maybe" RSVP affect capacity or waitlist status?
- How is waitlist order determined when multiple invitees are waiting?
- Can the host edit event details after creation, such as the date/time, location, or max-capacity?
- If the host lowers max-capacity below the current number of confirmed attendees, how should the existing confirmed attendees be handled?
- What is the behavioral difference between closing an event and cancelling an event?
- Can the host still view the event and RSVP information after closing or cancelling it?
- Is host authentication/account management part of this feature, or is it provided by an existing system outside the feature scope?
- Can the same email address be invited more than once to the same event?
- Can a declined invitee be re-invited to the same event?
- What time zone should be associated with an event's date/time, and how should the RSVP lock be evaluated across different time zones?

## Step 07 — Actors and Workflows

### Actors

- **Host** — the user who creates an event and becomes responsible for it.
- **Invitee** — a person invited by the host to respond to an event.

### Host Workflow

1. Creates an event with a title, description, date/time, location, and optional max-capacity.
   - If no max-capacity is set, no capacity limit is enforced for RSVP purposes.
2. Becomes the host of the event.
3. Invites people by email.
4. Views the live attendance dashboard (counts and list of attendees).
5. Closes the event to further responses.
6. Cancels the event.

### Invitee Workflow

1. Receives the unique invitation link via the host's email invite.
2. Responds Yes, No, or Maybe using the link.
   - See System-Triggered Behaviors for how the response is handled based on the event's capacity and status.
3. Changes their RSVP (Yes / No / Maybe) at any point before the event starts.
   - See System-Triggered Behaviors for how a changed response is handled based on the event's capacity and status.

### System-Triggered Behaviors

- If the event has no max-capacity, RSVP responses are recorded as given, with no waitlist logic involved.
- If the event has available capacity, a "Yes" response is recorded as confirmed.
- If the event is at max-capacity, a new "Yes" response is placed on the waitlist instead of confirmed.
- When a confirmed attendee changes their RSVP to No, a waitlisted attendee is automatically promoted to confirmed.
- After the event start time, all RSVPs are locked and can no longer be changed.
- Closed or cancelled events no longer accept further RSVP responses, as applicable to the feature brief.

## Step 08 — Invariants

- **Capacity invariant**: when an event has a max-capacity, the number of confirmed
  ("Yes") attendees never exceeds it. Any "Yes" that would exceed capacity goes to
  the waitlist instead of confirmed.
- **Lock-after-start invariant**: once an event's start time has passed, no invitee
  can create or change an RSVP for that event.
- **Waitlist promotion invariant**: a waitlisted invitee can only be promoted to
  confirmed when a confirmed capacity slot becomes available, and the promotion
  must not cause the confirmed attendee count to exceed the event's max-capacity.
- **Single host per event**: every event has exactly one host, fixed at creation
  (the event creator).
- **Single active RSVP per invitee**: an invitee has at most one current RSVP
  status (Yes / No / Maybe / waitlisted) for a given event at any time.
- **Closed/cancelled events accept no new RSVP activity**: once an event is closed
  or cancelled, no new or changed RSVP is accepted for it.

## Step 09 — First-Pass Architecture

### Components

- **Frontend (React + TypeScript, Vite)** — supports the host and invitee
  workflows defined in Step 07 (event creation and management, the attendance
  dashboard, and the RSVP link flow).
- **Backend (Spring Boot, Spring MVC, Spring Data JPA)** — the single place where
  business rules and invariants (capacity, waitlist, lock-after-start, single host,
  single active RSVP) are enforced; the authoritative source for whether an action
  is currently allowed.
- **Database (PostgreSQL)** — the persistent store for event, invitation, and
  RSVP-related state, at a high level.

### Interaction

- Frontend and backend run as separate processes communicating over HTTP (per the
  repo's setup: backend on :8280, frontend on :5171).
- All state-changing actions (create event, invite, RSVP, change RSVP, close,
  cancel) go through the backend, so invariants are enforced in one place rather
  than duplicated on the frontend.
- The backend and database are authoritative for business state; the frontend may
  hold temporary UI state but does not own or decide business state on its own.

### Deferred to later steps

- Specific API endpoints/contracts and detailed database schema are deferred beyond this first-pass architecture.
- Trust boundaries and security notes (Step 11)
- Concurrency handling for simultaneous RSVPs (Step 12)

## Step 10 — Data Ownership and State Model

### Event State Model

States (host-controlled):
- **Open** — default state after creation; accepting invitations and RSVPs. (Not
  named explicitly in the brief, but implied as the starting state, since invites
  and RSVPs must happen against *some* active state.)
- **Closed** — host has closed the event to further responses; existing RSVPs
  remain as recorded.
- **Cancelled** — host has cancelled the event.

Transitions:
- Open → Closed (host action)
- Open → Cancelled (host action)
- Whether Closed → Cancelled is a valid transition is **not defined** by the
  brief (see Step 06 open question on closing vs. cancelling) — not assumed here.

Time-based condition (not a separate Event state):
- Once the event's start time has passed, RSVP creation and changes are locked
  for that event, regardless of whether it is Open or Closed (Lock-after-start
  invariant, Step 08). This is a condition evaluated against the event's stored
  date/time, not a lifecycle state the Event transitions into.

### RSVP Model (per invitee, per event)

The invitee's **response** and their **attendance status** are separate concepts:

- **Response**: No response yet → one of **No / Maybe / Yes**.
- **Attendance status**: only applicable when Response = Yes:
  - **Confirmed** — counted against max-capacity.
  - **Waitlisted** — the Yes could not be confirmed because the event was at
    max-capacity.

"Waitlisted" is not a distinct response value — it is the attendance status of a
Yes response when capacity is unavailable.

Behavior:
- No response yet → Yes, with available capacity → Response = Yes, status = Confirmed.
- No response yet → Yes, at max-capacity → Response = Yes, status = Waitlisted.
- No response yet → No or Maybe → Response recorded, no attendance status.
- A Response = Yes / Confirmed changed to No frees a confirmed slot and triggers
  automatic promotion of a Waitlisted Yes to Confirmed (System-Triggered
  Behaviors, Step 07; Waitlist promotion invariant, Step 08).
- Changing from Yes/Confirmed to Maybe removes the Confirmed attendance status,
  because attendance status only applies when Response = Yes.
- Whether that newly available capacity slot automatically triggers waitlist
  promotion remains undefined and is still an open question from Step 06.
- Response changes are allowed only while the event is Open and before its start
  time.
- Closed or Cancelled events do not accept new or changed RSVP activity.
- After the event's start time, the RSVP (response and attendance status) is
  locked and cannot change further.

### Data Ownership

- The backend and database (Step 09) are the sole source of truth for Event state
  and for each invitee's Response and Attendance status.
- Attendance dashboard counts and attendee lists are **derived** from current
  Response/Attendance-status records at read time — not stored as separate,
  independently-owned state.
- The frontend does not own or persist business state; any state it holds is
  temporary UI state (per Step 09).

## Step 11 — Trust Boundaries and Security Notes

### Trust Boundaries

- **Host ↔ Backend**: the host is treated as an identifiable user of the system
  (Step 06 fact: "a user can create an event"), but the brief does not define how
  a host is authenticated — this remains an open question (Step 06), not assumed
  here.
- **Invitee ↔ Backend**: the invitee is not an authenticated account holder; their
  only credential is the unique link they were sent (Step 05 constraint). The
  unique invitation link is the mechanism currently defined by the brief for
  identifying which invitee's RSVP is being accessed. The brief does not define
  any additional identity verification.
- **Host ↔ Invitee**: these are distinct trust levels. Only the host can create,
  invite, view the dashboard, close, or cancel an event (Step 07 Host Workflow);
  an invitee can only respond/change their own RSVP (Step 07 Invitee Workflow;
  Step 04 non-goal: invitees cannot invite others). The backend must enforce this
  separation — an invitee's link must not grant host-level actions.
- **Frontend ↔ Backend**: per Step 09, the backend/database are authoritative for
  business state. Any validation the frontend performs (e.g., disabling a button
  when an event looks full or closed) is a UI convenience only — the backend must
  independently re-check every invariant (Step 08) on each request, since the
  frontend cannot be trusted to enforce them.

### Security Notes (first-pass; not yet resolved)

- The brief does not specify what makes the invitee's unique link secure (e.g.,
  how unguessable it is, whether it expires, whether it can be reused after the
  event is cancelled) — flagged as an open question, not assumed.
- The brief does not specify how a host authenticates at all — flagged as an open
  question (Step 06), carried forward here as a trust-boundary gap rather than
  resolved.
- Because the invitee's link is a bearer credential (whoever has it can act as
  that invitee), leaking or forwarding the link is a first-pass risk worth noting,
  though the brief gives no requirements for mitigating it.

## Step 12 — Concurrency and Correctness Notes

### Correctness principle

The invariants in Step 08 must hold under concurrent access, not only when
actions happen one at a time. Wherever a decision depends on reading current
state (e.g., "is there capacity?") and then writing a result (e.g., "confirm this
RSVP"), that read-then-write must be treated as a single atomic operation so two
concurrent requests cannot both act on the same stale read. The specific
mechanism for guaranteeing this is an implementation decision, not defined by the
brief, and is deferred beyond this design note.

### Scenario 1: Two simultaneous "Yes" RSVPs for the last spot

An event has one confirmed slot remaining. Two invitees submit a "Yes" response
at the same time. Without atomic check-and-assign, both could read "1 slot
available" and both be confirmed, violating the Capacity invariant. Correctness
requires exactly one of the two to end up Confirmed and the other Waitlisted —
never both Confirmed, and never a count exceeding max-capacity.

### Scenario 2: Waitlist promotion racing a new "Yes"

A confirmed attendee changes their RSVP to No, freeing a slot, at the same moment
a different invitee submits a new "Yes." Both the automatic promotion (Step 07/08)
and the new "Yes" are competing for the same freed slot. Correctness requires
only one of them to claim it — the other must be Waitlisted (or remain
Waitlisted), consistent with the Waitlist promotion invariant.

### Scenario 3: Event closed/cancelled while an RSVP is in flight

A host closes or cancels an event at the same moment an invitee's RSVP
submission is being processed. Correctness requires the "is this event still
accepting RSVP activity" check and the RSVP write to be evaluated together.
Correctness requires that the event's current lifecycle/time-based RSVP
eligibility and the RSVP write are evaluated consistently, so an RSVP cannot
bypass the Closed, Cancelled, or Lock-after-start rules merely because the
request began before the event state changed.

## Step 13 — Scalability and Multi-Tenancy Notes

### Scale

- The brief does not specify expected volumes — number of events, invitees per
  event, or concurrent RSVP submissions. No scale targets are assumed here; this
  is an open question if scale-driven decisions become necessary later.
- Independent of overall scale, the design already requires correctness under
  concurrent access to a single event's capacity (Step 12) — that requirement
  holds whether the system serves a handful of events or many.

### Multi-Tenancy

- The brief does not describe an organizational or account-level multi-tenancy
  model (e.g., multiple organizations sharing the system with isolated data). No
  such concept is introduced here.
- The only data-scoping boundary implied by the brief is per-event: each event's
  invite list, RSVPs, and attendance data belong to that event and its host
  (Step 10), and are not described as shared with or visible to other events'
  hosts or invitees.
- Whether a host can own/manage multiple events, and whether any cross-event view
  (e.g., "all events I host") is needed, is not stated in the brief — carried as
  an open question, not assumed.

## Step 14 — Risks and Failure Notes

- **Concurrency bugs violate the Capacity invariant**: if the atomic
  check-and-assign behavior required in Step 12 is not implemented correctly,
  concurrent "Yes" RSVPs could both be confirmed, exceeding max-capacity.
  Consequence: overbooking, directly violating the Capacity invariant in Step 08.

- **Waitlist promotion fails to trigger**: if the promotion described in
  Steps 07/08 is not reliably executed when a confirmed attendee changes to
  No, a freed slot could remain unfilled even though a waitlisted invitee is
  waiting. Consequence: incorrect attendance state and dashboard results.

- **Undefined host authentication creates an authorization risk**: because
  Steps 06 and 11 leave host authentication undefined, an implementation that
  does not resolve this before going live could allow an unauthorized party to
  perform host-only actions such as creating, closing, or cancelling events.

- **Time-based locking depends on the unresolved timezone question**: Step 06
  leaves event timezone handling open. If start time is compared
  inconsistently, the Lock-after-start invariant in Step 08 could trigger
  early or late. Consequence: RSVPs could be incorrectly accepted or rejected
  around the event start time.

- **Close vs. Cancel ambiguity leads to inconsistent behavior**: Steps 06/10
  leave the behavioral difference between Closed and Cancelled undefined.
  Without a resolved definition, an implementation could apply inconsistent
  rules to these states. Consequence: event lifecycle behavior may not match
  the host's intended action.

- **Unique link as the sole invitee credential is a security risk**: Step 11
  identifies the invitee link as a bearer credential, while the brief does not
  define link expiration, reuse, or other protection requirements. If the link
  is leaked or forwarded, another party could potentially change the associated
  invitee's RSVP.

## Step 15 — Alternatives and Tradeoffs

- **Enforcing capacity/waitlist atomicity (Step 12)**
  - Option A: a single atomic database operation performs the capacity check and
    the confirm/waitlist assignment together.
  - Option B: application-level locking/synchronization around the same
    check-then-write.
  - Tradeoff: Option A can preserve correctness when the application runs as
    multiple instances; Option B can be simpler to reason about in application
    code but requires careful handling if multiple application instances are
    running. Not decided here — Step 12 deliberately leaves the mechanism
    unspecified.

- **Invitee link construction (Steps 05/11)**
  - Option A: a random, unguessable token stored against the invitee record and
    looked up on each request.
  - Option B: a self-contained signed token encoding invitee/event identity and
    validated without a database lookup.
  - Tradeoff: Option A makes revocation or reissue easier but requires a stored
    lookup; Option B reduces lookup requirements but is harder to revoke once
    issued. The link's security properties remain an open question from
    Step 11; these are candidate resolutions, not a decision.

- **Computing attendance dashboard counts (Step 10)**
  - Option A: compute counts from the current RSVP records at request time.
  - Option B: maintain stored counters that are updated when RSVP state changes.
  - Tradeoff: Option A follows Step 10's principle that dashboard values are
    derived from current RSVP state, but requires reading RSVP data when the
    dashboard is requested. Option B can reduce dashboard read work but
    introduces additional state that must remain consistent with the RSVP
    records. No change to the Step 10 design is made here.

- **Waitlist ordering (open question, Step 06)**
  - Option A: first-come-first-served, based on when an invitee becomes
    waitlisted.
  - Option B: define another deterministic ordering rule as part of the
    requirements before implementation.
  - Tradeoff: Option A is predictable and simple to implement; Option B leaves
    the business rule open until the ordering requirement is clarified.
    This remains an open question — no waitlist ordering rule is decided here.

## Step 16 — Rollout / Migration Notes

- **No existing data to migrate**: the feature is built on a fresh, empty database
  (Step 01 setup: `CREATE DATABASE hero; CREATE SCHEMA hero`). There is no prior
  schema, no existing events, and no existing RSVPs to migrate.

- **No prior version to stay compatible with**: this is the first version of the
  Event RSVP Manager feature; there are no existing API consumers or stored data
  formats that a rollout needs to remain backward-compatible with.

- **Scope of "rollout" for this task**: per the README, implementation is a
  stretch goal producing a local, working implementation in `hero-backend/` and
  `hero-frontend/` — not a staged or phased production release. No rollout
  strategy (feature flags, gradual exposure, or canarying) is required or
  described by the brief.

- **Not addressed here**: how a future production rollout of this feature would
  handle schema evolution, deployment sequencing, or coexisting with real user
  data is not defined by the brief. These remain open questions if the design
  later moves beyond the current scope.

## Step 17 — Assembled Draft: Completeness Check

This document (Steps 01–16) is the first complete design draft. Mapped against
the README's Evaluation Criteria:

- Clear problem statement → Step 03
- Bounded scope (explicit non-goals) → Step 04
- Visible assumptions, separated from facts → Step 06
- Explicit workflows for all key actors → Step 07
- Named invariants → Step 08
- Real architecture boundaries → Step 09
- Explicit state ownership → Step 10
- First-pass trust / concurrency / scale treatment → Steps 11, 12, 13
- Visible risks and tradeoffs → Steps 14, 15
- Unresolved open questions listed → Step 06, with additional items surfaced in
  Steps 08, 10, and 14

All criteria are addressed by an existing step. Step 18 will run the pre-review
weakness check against this draft as a whole.

## Step 18 — Pre-Review Weakness Check

### Highest-priority gaps (block a correct implementation if left unresolved)

1. **Host authentication is undefined, but other sections assume it exists.**
   Step 09 calls the backend authoritative, and Step 11 requires the backend to
   enforce the Host ↔ Invitee trust boundary. However, Steps 06 and 11 do not
   define a host identity or authentication model. Before implementation, the
   design needs a way to determine which requests are authorized to perform
   host-only actions.

2. **Event editing and capacity changes are unresolved.**
   Step 06 leaves whether the host can edit event details after creation open,
   including max-capacity. The Capacity invariant in Step 08 requires confirmed
   attendance to remain within max-capacity. If future requirements allow a
   host to lower capacity below the current confirmed count, the design must
   define how that situation is handled. The current invariant itself does not
   need to change; the unresolved issue is the behavior of a capacity change,
   if such editing is allowed.

3. **Timezone handling is unresolved, but the Lock-after-start invariant
   depends on it.**
   Step 08's lock condition compares the current time with the event start time.
   Step 06 leaves event timezone handling open. Before implementation, the design
   needs a consistent interpretation of the stored event start time so that the
   lock occurs at the correct moment.

### Medium-priority gaps

4. **Close vs. Cancel are modeled as distinct states (Step 10), but the brief
   does not currently define a behavioral difference between them.**
   Both states prevent further RSVP activity. The design should eventually
   clarify whether they have different semantics or whether the distinction
   should remain only as an event lifecycle distinction.

5. **The Yes → Maybe RSVP transition needs clearer wording.**
   Step 10 states that attendance status applies only when Response = Yes.
   Therefore, changing from Yes/Confirmed to Maybe removes the Confirmed
   attendance status. Whether this removal should automatically trigger
   waitlist promotion remains unresolved. This distinction should be
   clarified before implementation.

6. **Waitlist ordering (Steps 06/15) remains unresolved, but does not affect
   the core capacity invariant.**
   The ordering rule affects which waitlisted invitee is promoted when a slot
   becomes available, but the capacity and concurrency invariants can still
   hold regardless of the eventual ordering rule.

### Confirmed solid

- Each of the three named invariants in Step 08 has a corresponding concurrency
  scenario in Step 12 and a related failure note in Step 14.
- The non-goals in Step 04 are consistent with the trust boundaries in Step 11.
- The open questions introduced in Step 06 are carried into later design
  discussion rather than being silently dropped.

### Conclusion

The draft is substantially coherent. The main items that should be resolved
before implementation are host authentication/authorization, timezone
handling, and the behavior of event capacity changes if event editing is
allowed. The Yes → Maybe promotion behavior and Close vs. Cancel semantics
should also be clarified. Waitlist ordering can remain an open business-rule
question without blocking the core capacity design.
