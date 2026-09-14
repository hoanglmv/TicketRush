import api from './axios';
import { ApiResponse, TicketResponse } from '../types';

export const bookingApi = {
  lockSeat: (seatId: number) => api.post<ApiResponse<TicketResponse>>(`/seats/${seatId}/lock`),
  confirmPayment: (ticketId: number) => api.post<ApiResponse<TicketResponse>>(`/tickets/${ticketId}/confirm`),
  cancelTicket: (ticketId: number) => api.delete<ApiResponse<void>>(`/tickets/${ticketId}`),
  myTickets: () => api.get<ApiResponse<TicketResponse[]>>('/tickets/my'),
  getTicket: (ticketId: number) => api.get<ApiResponse<TicketResponse>>(`/tickets/${ticketId}`),
};
