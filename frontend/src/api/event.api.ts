import api from './axios';
import { ApiResponse, EventResponse, ZoneResponse, SeatResponse } from '../types';

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
