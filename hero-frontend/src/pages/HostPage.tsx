import { useEffect, useState, type FormEvent } from 'react';
import { useParams } from 'react-router-dom';
import { ApiError } from '../services/apiClient';
import { cancelEvent, closeEvent, createInvitation, getEvent } from '../services/eventsApi';
import { getHostToken } from '../services/hostTokenStorage';
import type { EventResponse, InvitationResponse } from '../types';

function HostPage() {
  const { id } = useParams<{ id: string }>();

  const [hostTokenInput, setHostTokenInput] = useState('');
  const [activeHostToken, setActiveHostToken] = useState<string | null>(null);
  const [event, setEvent] = useState<EventResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [actionSubmitting, setActionSubmitting] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);

  const [inviteEmail, setInviteEmail] = useState('');
  const [inviteSubmitting, setInviteSubmitting] = useState(false);
  const [inviteError, setInviteError] = useState<string | null>(null);
  const [invitations, setInvitations] = useState<InvitationResponse[]>([]);

  async function loadEvent(token: string) {
    if (!id) return;
    setLoading(true);
    setLoadError(null);
    try {
      const result = await getEvent(id, token);
      setEvent(result);
      setActiveHostToken(token);
    } catch (err) {
      setEvent(null);
      setActiveHostToken(null);
      setLoadError(err instanceof ApiError ? err.message : 'Could not load this event.');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    if (!id) return;
    const stored = getHostToken(id);
    if (stored) {
      setHostTokenInput(stored);
      loadEvent(stored);
    }
    // Only re-run if the event id in the URL changes.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  function handleTokenSubmit(e: FormEvent) {
    e.preventDefault();
    loadEvent(hostTokenInput);
  }

  async function handleClose() {
    if (!id || !activeHostToken) return;
    setActionSubmitting(true);
    setActionError(null);
    try {
      setEvent(await closeEvent(id, activeHostToken));
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : 'Could not close the event.');
    } finally {
      setActionSubmitting(false);
    }
  }

  async function handleCancel() {
    if (!id || !activeHostToken) return;
    setActionSubmitting(true);
    setActionError(null);
    try {
      setEvent(await cancelEvent(id, activeHostToken));
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : 'Could not cancel the event.');
    } finally {
      setActionSubmitting(false);
    }
  }

  async function handleInvite(e: FormEvent) {
    e.preventDefault();
    if (!id || !activeHostToken) return;
    setInviteSubmitting(true);
    setInviteError(null);
    try {
      const invitation = await createInvitation(id, activeHostToken, { email: inviteEmail });
      setInvitations((prev) => [...prev, invitation]);
      setInviteEmail('');
    } catch (err) {
      setInviteError(err instanceof ApiError ? err.message : 'Could not create the invitation.');
    } finally {
      setInviteSubmitting(false);
    }
  }

  if (!event) {
    return (
      <div className="page">
        <h1>Host page</h1>
        <p>Event id: {id}</p>
        <p>Enter the host token you received when this event was created.</p>
        <form onSubmit={handleTokenSubmit} className="form">
          <label>
            Host token
            <input value={hostTokenInput} onChange={(e) => setHostTokenInput(e.target.value)} required />
          </label>
          {loadError && <p className="error-message">{loadError}</p>}
          <button type="submit" disabled={loading}>
            {loading ? 'Loading…' : 'Load event'}
          </button>
        </form>
      </div>
    );
  }

  const canClose = event.status === 'OPEN';
  const canCancel = event.status === 'OPEN' || event.status === 'CLOSED';
  const canInvite = event.status === 'OPEN';

  return (
    <div className="page">
      <h1>Host dashboard</h1>

      <section className="event-summary">
        <h2>{event.title}</h2>
        {event.description && <p>{event.description}</p>}
        <p>Date/time: {new Date(event.startTime).toLocaleString()}</p>
        <p>Location: {event.location}</p>
        <p>Capacity: {event.maxCapacity ?? 'Unlimited'}</p>
        <p>
          Status: <strong>{event.status}</strong>
        </p>
      </section>

      <section className="event-actions">
        {actionError && <p className="error-message">{actionError}</p>}
        {canClose && (
          <button onClick={handleClose} disabled={actionSubmitting}>
            Close event
          </button>
        )}
        {canCancel && (
          <button onClick={handleCancel} disabled={actionSubmitting}>
            Cancel event
          </button>
        )}
        {event.status === 'CANCELLED' && <p>This event is cancelled. No further actions are available.</p>}
      </section>

      <section className="invitations">
        <h3>Invite someone</h3>
        {!canInvite && <p>Invitations can only be sent while the event is open.</p>}
        {canInvite && (
          <form onSubmit={handleInvite} className="form">
            <label>
              Email
              <input
                type="email"
                value={inviteEmail}
                onChange={(e) => setInviteEmail(e.target.value)}
                required
              />
            </label>
            {inviteError && <p className="error-message">{inviteError}</p>}
            <button type="submit" disabled={inviteSubmitting}>
              {inviteSubmitting ? 'Sending…' : 'Create invitation'}
            </button>
          </form>
        )}

        {invitations.length > 0 && (
          <div className="invitation-list">
            <h4>Invitations created this session</h4>
            <ul>
              {invitations.map((invitation) => (
                <li key={invitation.id}>
                  {invitation.email} — <code>{`${window.location.origin}/rsvp/${invitation.rsvpToken}`}</code>
                </li>
              ))}
            </ul>
            <p className="note">
              This list is only kept for this browser session. The current API has no endpoint to
              fetch the full invitee list or attendance counts after the fact.
            </p>
          </div>
        )}
      </section>
    </div>
  );
}

export default HostPage;
