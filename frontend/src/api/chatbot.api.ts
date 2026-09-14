import api from './axios';
import { ApiResponse } from '../types';

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
