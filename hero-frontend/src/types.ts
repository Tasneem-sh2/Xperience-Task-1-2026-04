export type EventStatus = 'OPEN' | 'CLOSED' | 'CANCELLED';

export type RsvpResponse = 'YES' | 'NO' | 'MAYBE';

export type AttendanceStatus = 'CONFIRMED' | 'WAITLISTED';

// Mirrors the backend's EventResponse DTO exactly - deliberately has no
// hostToken field, matching the backend's own decision not to expose it here.
export interface EventResponse {
  id: number;
  title: string;
  description: string | null;
  startTime: string; // ISO-8601, e.g. "2027-01-10T09:00:00Z"
  location: string;
  maxCapacity: number | null;
  status: EventStatus;
}

// Mirrors EventCreatedResponse - the one response shape that includes hostToken.
export interface EventCreatedResponse extends EventResponse {
  hostToken: string;
}

export interface CreateEventRequest {
  title: string;
  description?: string;
  startTime: string; // ISO-8601, sent as UTC
  location: string;
  maxCapacity?: number;
}

export interface InvitationResponse {
  id: number;
  eventId: number;
  email: string;
  rsvpToken: string;
  rsvpLink: string;
}

export interface CreateInvitationRequest {
  email: string;
}

// Mirrors the backend's ErrorResponse DTO.
export interface ApiErrorResponse {
  status: number;
  error: string;
  message: string;
}
