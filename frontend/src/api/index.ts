import api from './axios';
import axios from 'axios';
import { ApiResponse, AuthResponse, EventResponse, ZoneResponse, SeatResponse, TicketResponse, QueueStatusResponse } from '../types';

// ========== AUTH ==========
export const authApi = {
  register: (data: { username: string; password: string; email: string; fullName?: string; phone?: string; dateOfBirth?: string; gender?: string }) =>
    api.post<ApiResponse<AuthResponse>>('/auth/register', data),
  login: (data: { username: string; password: string }) =>
    api.post<ApiResponse<AuthResponse>>('/auth/login', data),
  me: () => api.get<ApiResponse<AuthResponse>>('/auth/me'),
  changePassword: (data: { oldPassword: string; newPassword: string }) => 
    api.post<ApiResponse<void>>('/auth/change-password', data),
  checkUsername: (username: string) => api.get<ApiResponse<boolean>>(`/auth/check-username?username=${encodeURIComponent(username)}`),
  checkEmail: (email: string) => api.get<ApiResponse<boolean>>(`/auth/check-email?email=${encodeURIComponent(email)}`),
};

// ========== USERS ==========
export const userApi = {
  getProfile: () => api.get<ApiResponse<any>>('/users/me'),
  updateProfile: (data: any) => api.put<ApiResponse<any>>('/users/me', data),
};



// ========== EVENTS (Public) ==========
export const eventApi = {
  list: () => api.get<ApiResponse<EventResponse[]>>('/events'),
  get: (id: number) => api.get<ApiResponse<EventResponse>>(`/events/${id}`),
  search: (q?: string, category?: string, city?: string, startDate?: string, endDate?: string) => {
    let url = '/events/search?';
    if (q) url += `q=${encodeURIComponent(q)}&`;
    if (category && category !== 'AllCategories') url += `category=${category}&`;
    if (city && city !== 'AllCities') url += `city=${encodeURIComponent(city)}&`;
    if (startDate) url += `startDate=${startDate}&`;
    if (endDate) url += `endDate=${endDate}&`;
    return api.get<ApiResponse<EventResponse[]>>(url);
  },
  zones: (eventId: number) => api.get<ApiResponse<ZoneResponse[]>>(`/events/${eventId}/zones`),
  seats: (eventId: number) => api.get<ApiResponse<SeatResponse[]>>(`/events/${eventId}/seats`),
};

// ========== BOOKING ==========
export const bookingApi = {
  lockSeat: (seatId: number) => api.post<ApiResponse<TicketResponse>>(`/seats/${seatId}/lock`),
  confirmPayment: (ticketId: number) => api.post<ApiResponse<TicketResponse>>(`/tickets/${ticketId}/confirm`),
  cancelTicket: (ticketId: number) => api.delete<ApiResponse<void>>(`/tickets/${ticketId}`),
  myTickets: () => api.get<ApiResponse<TicketResponse[]>>('/tickets/my'),
  getTicket: (ticketId: number) => api.get<ApiResponse<TicketResponse>>(`/tickets/${ticketId}`),
};



// ========== QUEUE ==========
export const queueApi = {
  join: (eventId: number) => api.post<ApiResponse<QueueStatusResponse>>(`/queue/${eventId}/join`),
  status: (eventId: number) => api.get<ApiResponse<QueueStatusResponse>>(`/queue/${eventId}/status`),
  leave: (eventId: number) => api.delete<ApiResponse<void>>(`/queue/${eventId}/leave`),
};

// ========== SETTINGS ==========
export const settingApi = {
  getAll: () => api.get('/public/settings'),
  saveAll: (data: Record<string, string>) => api.post('/admin/settings', data)
};

// ========== ADMIN ==========
export const adminApi = {
  dashboard: () => api.get<ApiResponse<Record<string, any>>>('/admin/dashboard'),
  events: () => api.get<ApiResponse<EventResponse[]>>('/admin/events'),
  createEvent: (data: any) => api.post<ApiResponse<EventResponse>>('/admin/events', data),
  updateEvent: (id: number, data: any) => api.put<ApiResponse<EventResponse>>(`/admin/events/${id}`, data),
  deleteEvent: (id: number) => api.delete<ApiResponse<void>>(`/admin/events/${id}`),
  updateStatus: (id: number, status: string) => api.put<ApiResponse<EventResponse>>(`/admin/events/${id}/status`, null, { params: { status } }),
  createZone: (eventId: number, data: any) => api.post<ApiResponse<ZoneResponse>>(`/admin/events/${eventId}/zones`, data),
  updateZoneOrder: (eventId: number, zoneIds: number[]) => api.put<ApiResponse<void>>(`/admin/events/${eventId}/zones/reorder`, zoneIds),
  eventStats: (eventId: number) => api.get<ApiResponse<Record<string, any>>>(`/admin/events/${eventId}/stats`),
  demographics: (eventId: number) => api.get<ApiResponse<Record<string, any>>>(`/admin/events/${eventId}/demographics`),
  uploadImage: (formData: FormData) => {
    return axios.post<ApiResponse<string>>('/api/upload', formData, {
      headers: {
        Authorization: `Bearer ${localStorage.getItem('token')}`
      }
    });
  },
};

// ========== CHATBOT AGENT & RAG MONITOR ==========
export const chatbotApi = {
  sendMessage: (message: string) =>
    api.post<ApiResponse<any>>('/chatbot/message', { message }),
  getWelcome: () =>
    api.get<ApiResponse<any>>('/chatbot/welcome'),
  sendFeedback: (logId: string, positive: boolean) =>
    api.post<ApiResponse<boolean>>('/chatbot/feedback', { logId, positive }),
  getMetrics: () =>
    api.get<ApiResponse<any>>('/chatbot/metrics'),
  runRagasEvaluation: () =>
    api.post<ApiResponse<any>>('/chatbot/ragas/evaluate'),
  getLatestRagas: () =>
    api.get<ApiResponse<any>>('/chatbot/ragas/latest'),
};
