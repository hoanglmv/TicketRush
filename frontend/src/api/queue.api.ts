import api from './axios';
import { ApiResponse, QueueStatusResponse } from '../types';

export const queueApi = {
  join: (eventId: number) => api.post<ApiResponse<QueueStatusResponse>>(`/queue/${eventId}/join`),
  status: (eventId: number) => api.get<ApiResponse<QueueStatusResponse>>(`/queue/${eventId}/status`),
  leave: (eventId: number) => api.delete<ApiResponse<void>>(`/queue/${eventId}/leave`),
};
