import api from './axios';
import axios from 'axios';
import { ApiResponse, EventResponse, ZoneResponse } from '../types';

export const settingApi = {
  getAll: () => api.get('/public/settings'),
  saveAll: (data: Record<string, string>) => api.post('/admin/settings', data)
};

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
