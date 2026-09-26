import { apiFetch } from './apiClient';
import type { RsvpResponse, RsvpResponseDto, SubmitRsvpRequest } from '../types';

export function getRsvp(rsvpToken: string): Promise<RsvpResponseDto> {
  return apiFetch<RsvpResponseDto>(`/api/rsvp/${rsvpToken}`);
}

export function submitRsvp(rsvpToken: string, response: RsvpResponse): Promise<RsvpResponseDto> {
  const body: SubmitRsvpRequest = { response };
  return apiFetch<RsvpResponseDto>(`/api/rsvp/${rsvpToken}`, { method: 'POST', body });
}
