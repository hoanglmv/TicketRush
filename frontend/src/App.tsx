import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider, useAuth } from './contexts/AuthContext';
import { SettingsProvider } from './contexts/SettingsContext';
import { LanguageProvider } from './i18n';
import Navbar from './components/Navbar';
import HomePage from './pages/HomePage';
import Footer from './components/Footer';
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';
import EventListPage from './pages/EventListPage';
import EventDetailPage from './pages/EventDetailPage';
import SeatSelectionPage from './pages/SeatSelectionPage';
import CheckoutPage from './pages/CheckoutPage';
import WaitingRoomPage from './pages/WaitingRoomPage';
import MyTicketsPage from './pages/MyTicketsPage';
import AdminDashboardPage from './pages/admin/AdminDashboardPage';
import AdminEventListPage from './pages/admin/AdminEventListPage';
import AdminEventCreatePage from './pages/admin/AdminEventCreatePage';
import AdminEventEditPage from './pages/admin/AdminEventEditPage';
import AdminEventDetailPage from './pages/admin/AdminEventDetailPage';
import ProfilePage from './pages/ProfilePage';
import ChatbotWidget from './components/ChatbotWidget';
import './index.css';

function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const { isAuthenticated } = useAuth();
  return isAuthenticated ? <>{children}</> : <Navigate to="/login" />;
}

function AdminRoute({ children }: { children: React.ReactNode }) {
  const { isAuthenticated, isAdmin } = useAuth();
  if (!isAuthenticated) return <Navigate to="/login" />;
  if (!isAdmin) return <Navigate to="/" />;
  return <>{children}</>;
}

function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<HomePage />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/events" element={<EventListPage />} />
      <Route path="/events/:id" element={<EventDetailPage />} />
      <Route path="/events/:id/seats" element={<ProtectedRoute><SeatSelectionPage /></ProtectedRoute>} />
      <Route path="/events/:eventId/queue" element={<ProtectedRoute><WaitingRoomPage /></ProtectedRoute>} />
      <Route path="/checkout/:ticketId" element={<ProtectedRoute><CheckoutPage /></ProtectedRoute>} />
      <Route path="/tickets" element={<ProtectedRoute><MyTicketsPage /></ProtectedRoute>} />
      <Route path="/profile" element={<ProtectedRoute><ProfilePage /></ProtectedRoute>} />
      <Route path="/admin" element={<AdminRoute><AdminDashboardPage /></AdminRoute>} />
      <Route path="/admin/events" element={<AdminRoute><AdminEventListPage /></AdminRoute>} />
      <Route path="/admin/events/create" element={<AdminRoute><AdminEventCreatePage /></AdminRoute>} />
      <Route path="/admin/events/:id/edit" element={<AdminRoute><AdminEventEditPage /></AdminRoute>} />
      <Route path="/admin/events/:id" element={<AdminRoute><AdminEventDetailPage /></AdminRoute>} />
    </Routes>
  );
}

function App() {
  return (
    <BrowserRouter>
      <LanguageProvider>
        <AuthProvider>
          <SettingsProvider>
            <div className="min-h-screen flex flex-col bg-gray-50 text-gray-900 antialiased font-sans">
              <Navbar />
              <div className="flex-1 flex flex-col">
                <AppRoutes />
              </div>
              <ChatbotWidget />
              <Footer />
            </div>
          </SettingsProvider>
        </AuthProvider>
      </LanguageProvider>
    </BrowserRouter>
  );
}

export default App;
