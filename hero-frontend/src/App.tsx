import { Route, Routes } from 'react-router-dom';
import './App.css';
import HomePage from './pages/HomePage';
import HostPage from './pages/HostPage';
import NotFoundPage from './pages/NotFoundPage';
import RsvpPage from './pages/RsvpPage';

function App() {
  return (
    <Routes>
      <Route path="/" element={<HomePage />} />
      <Route path="/host/:id" element={<HostPage />} />
      <Route path="/rsvp/:token" element={<RsvpPage />} />
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}

export default App;
