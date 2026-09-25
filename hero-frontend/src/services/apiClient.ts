import type { ApiErrorResponse } from '../types';

export const API_BASE_URL = 'http://localhost:8280';

export class ApiError extends Error {
  readonly status: number;
  readonly error: string;

  constructor(body: ApiErrorResponse) {
    super(body.message);
    this.status = body.status;
    this.error = body.error;
  }
}

interface ApiFetchOptions {
  method?: string;
  hostToken?: string;
  body?: unknown;
}

// Generic request helper shared by every endpoint-specific function in
// eventsApi.ts. Parses the backend's {status, error, message} error shape
// instead of inventing a different one.
export async function apiFetch<T>(path: string, options: ApiFetchOptions = {}): Promise<T> {
  const headers: Record<string, string> = {};
  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }
  if (options.hostToken) {
    headers['X-Host-Token'] = options.hostToken;
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    method: options.method ?? 'GET',
    headers,
    body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
  });

  const text = await response.text();
  const data: unknown = text ? JSON.parse(text) : undefined;

  if (!response.ok) {
    const isErrorShape =
      data !== null &&
      typeof data === 'object' &&
      'status' in data &&
      'error' in data &&
      'message' in data;
    const errorBody: ApiErrorResponse = isErrorShape
      ? (data as ApiErrorResponse)
      : { status: response.status, error: response.statusText, message: 'Request failed' };
    throw new ApiError(errorBody);
  }

  return data as T;
}
