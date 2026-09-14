import api from './axios';
import { ApiResponse, AuthResponse } from '../types';

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

export const userApi = {
  getProfile: () => api.get<ApiResponse<any>>('/users/me'),
  updateProfile: (data: any) => api.put<ApiResponse<any>>('/users/me', data),
};
