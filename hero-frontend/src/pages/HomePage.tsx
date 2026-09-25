import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { ApiError } from '../services/apiClient';
import { createEvent } from '../services/eventsApi';
import { saveHostToken } from '../services/hostTokenStorage';
import type { EventCreatedResponse } from '../types';

function HomePage() {
  const navigate = useNavigate();

  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [startTime, setStartTime] = useState('');
  const [location, setLocation] = useState('');
  const [maxCapacity, setMaxCapacity] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [created, setCreated] = useState<EventCreatedResponse | null>(null);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      const event = await createEvent({
        title,
        description: description || undefined,
        startTime: new Date(startTime).toISOString(),
        location,
        maxCapacity: maxCapacity ? Number(maxCapacity) : undefined,
      });
      saveHostToken(event.id, event.hostToken);
      setCreated(event);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Something went wrong. Please try again.');
    } finally {
      setSubmitting(false);
    }
  }

  function goToDashboard() {
    if (!created) return;
    navigate(`/host/${created.id}`);
  }

  if (created) {
    return (
      <div className="page">
        <h1>Event created</h1>
        <p>
          Your event "{created.title}" has been created (id: {created.id}).
        </p>
        <div className="host-token-box">
          <strong>Host token — save this, it will not be shown again:</strong>
          <code>{created.hostToken}</code>
        </div>
        <button onClick={goToDashboard}>Go to host dashboard</button>
      </div>
    );
  }

  return (
    <div className="page">
      <h1>Event RSVP Manager</h1>
      <p>Create a new event to get started.</p>

      <form onSubmit={handleSubmit} className="form">
        <label>
          Title
          <input value={title} onChange={(e) => setTitle(e.target.value)} required />
        </label>
        <label>
          Description
          <textarea value={description} onChange={(e) => setDescription(e.target.value)} />
        </label>
        <label>
          Start date/time
          <input
            type="datetime-local"
            value={startTime}
            onChange={(e) => setStartTime(e.target.value)}
            required
          />
        </label>
        <label>
          Location
          <input value={location} onChange={(e) => setLocation(e.target.value)} required />
        </label>
        <label>
          Max capacity (optional)
          <input
            type="number"
            min={1}
            value={maxCapacity}
            onChange={(e) => setMaxCapacity(e.target.value)}
          />
        </label>

        {error && <p className="error-message">{error}</p>}

        <button type="submit" disabled={submitting}>
          {submitting ? 'Creating…' : 'Create event'}
        </button>
      </form>
    </div>
  );
}

export default HomePage;
