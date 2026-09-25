import { useParams } from 'react-router-dom';

function RsvpPage() {
  const { token } = useParams<{ token: string }>();

  return (
    <div className="page">
      <h1>RSVP page</h1>
      <p>RSVP token: {token}</p>
    </div>
  );
}

export default RsvpPage;
