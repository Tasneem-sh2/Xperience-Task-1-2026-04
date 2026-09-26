import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { ApiError } from '../services/apiClient';
import { getRsvp, submitRsvp } from '../services/rsvpApi';
import type { RsvpResponse, RsvpResponseDto } from '../types';

function RsvpPage() {
  const { token } = useParams<{ token: string }>();

  const [rsvp, setRsvp] = useState<RsvpResponseDto | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [justSubmitted, setJustSubmitted] = useState(false);

  useEffect(() => {
    if (!token) return;
    let cancelled = false;

    setLoading(true);
    setLoadError(null);

    getRsvp(token)
      .then((result) => {
        if (!cancelled) setRsvp(result);
      })
      .catch((err) => {
        if (!cancelled) {
          setLoadError(err instanceof ApiError ? err.message : 'Could not load this invitation.');
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [token]);

  async function handleRespond(value: RsvpResponse) {
    // The route param is always defined once this page has rendered past the
    // loading/error states below, and disabling while submitting prevents
    // duplicate submissions.
    if (!token || submitting) return;
    setSubmitting(true);
    setSubmitError(null);
    setJustSubmitted(false);
    try {
      const result = await submitRsvp(token, value);
      // The backend response is authoritative - replace the displayed state
      // with it wholesale rather than guessing the outcome client-side (a
      // YES may come back CONFIRMED or WAITLISTED depending on capacity).
      setRsvp(result);
      setJustSubmitted(true);
    } catch (err) {
      setSubmitError(err instanceof ApiError ? err.message : 'Could not update your RSVP.');
    } finally {
      setSubmitting(false);
    }
  }

  if (loading) {
    return (
      <div className="page">
        <h1>RSVP</h1>
        <p>Loading…</p>
      </div>
    );
  }

  if (loadError || !rsvp) {
    return (
      <div className="page">
        <h1>RSVP</h1>
        <p className="error-message">{loadError ?? 'This invitation could not be found.'}</p>
      </div>
    );
  }

  return (
    <div className="page">
      <h1>{rsvp.eventTitle}</h1>
      {rsvp.eventDescription && <p>{rsvp.eventDescription}</p>}
      <p>Date/time: {new Date(rsvp.eventStartTime).toLocaleString()}</p>
      <p>Location: {rsvp.eventLocation}</p>
      <p>
        Event status: <strong>{rsvp.eventStatus}</strong>
      </p>

      <section className="rsvp-status">
        {rsvp.response === null && <p>You haven't responded yet.</p>}
        {rsvp.response === 'YES' && rsvp.attendanceStatus === 'CONFIRMED' && (
          <p>
            You responded <strong>Yes</strong> — you're confirmed.
          </p>
        )}
        {rsvp.response === 'YES' && rsvp.attendanceStatus === 'WAITLISTED' && (
          <p>
            You responded <strong>Yes</strong> — you're currently on the waitlist. You'll be
            confirmed automatically if a spot opens up.
          </p>
        )}
        {rsvp.response === 'MAYBE' && (
          <p>
            You responded <strong>Maybe</strong>.
          </p>
        )}
        {rsvp.response === 'NO' && (
          <p>
            You responded <strong>No</strong>.
          </p>
        )}
      </section>

      <section className="rsvp-choices">
        <p>{rsvp.response ? 'Change your response:' : 'Please respond:'}</p>
        <div className="rsvp-buttons">
          <button
            type="button"
            className={rsvp.response === 'YES' ? 'selected' : ''}
            onClick={() => handleRespond('YES')}
            disabled={submitting}
          >
            Yes
          </button>
          <button
            type="button"
            className={rsvp.response === 'MAYBE' ? 'selected' : ''}
            onClick={() => handleRespond('MAYBE')}
            disabled={submitting}
          >
            Maybe
          </button>
          <button
            type="button"
            className={rsvp.response === 'NO' ? 'selected' : ''}
            onClick={() => handleRespond('NO')}
            disabled={submitting}
          >
            No
          </button>
        </div>

        {submitting && <p>Submitting…</p>}
        {submitError && <p className="error-message">{submitError}</p>}
        {justSubmitted && !submitError && (
          <p className="success-message">Your response has been recorded.</p>
        )}
      </section>
    </div>
  );
}

export default RsvpPage;
