import { apiFetch } from './apiClient';
import type {
  CreateEventRequest,
  CreateInvitationRequest,
  EventCreatedResponse,
  EventResponse,
  InvitationResponse,
} from '../types';

export function createEvent(request: CreateEventRequest): Promise<EventCreatedResponse> {
  return apiFetch<EventCreatedResponse>('/api/events', { method: 'POST', body: request });
}

export function getEvent(eventId: string, hostToken: string): Promise<EventResponse> {
  return apiFetch<EventResponse>(`/api/events/${eventId}`, { hostToken });
}

export function closeEvent(eventId: string, hostToken: string): Promise<EventResponse> {
  return apiFetch<EventResponse>(`/api/events/${eventId}/close`, { method: 'POST', hostToken });
}

export function cancelEvent(eventId: string, hostToken: string): Promise<EventResponse> {
  return apiFetch<EventResponse>(`/api/events/${eventId}/cancel`, { method: 'POST', hostToken });
}

export function createInvitation(
  eventId: string,
  hostToken: string,
  request: CreateInvitationRequest,
): Promise<InvitationResponse> {
  return apiFetch<InvitationResponse>(`/api/events/${eventId}/invitations`, {
    method: 'POST',
    hostToken,
    body: request,
  });
}
