// Stores the host token in sessionStorage, scoped per event id, so different
// events don't overwrite each other's token and nothing survives past the
// browser tab being closed. Wrapped in try/catch since sessionStorage can
// throw in some privacy modes - the manual token input remains the fallback.

const STORAGE_KEY_PREFIX = 'hostToken:';

export function saveHostToken(eventId: number | string, hostToken: string): void {
  try {
    sessionStorage.setItem(`${STORAGE_KEY_PREFIX}${eventId}`, hostToken);
  } catch {
    // sessionStorage unavailable - persistence is best-effort only.
  }
}

export function getHostToken(eventId: string): string | null {
  try {
    return sessionStorage.getItem(`${STORAGE_KEY_PREFIX}${eventId}`);
  } catch {
    return null;
  }
}
