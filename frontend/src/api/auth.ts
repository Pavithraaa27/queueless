import { api } from './client';
import type { AuthUser, Role } from '../types';

export interface RegisterPayload {
  fullName: string;
  email: string;
  password: string;
  phoneNumber?: string;
  role: Role;
}

export interface LoginPayload {
  email: string;
  password: string;
}

interface AuthResponse {
  token: string;
  userId: number;
  fullName: string;
  email: string;
  role: Role;
}

function toAuthUser(res: AuthResponse): AuthUser {
  return {
    token: res.token,
    userId: res.userId,
    fullName: res.fullName,
    email: res.email,
    role: res.role,
  };
}

export async function register(payload: RegisterPayload): Promise<AuthUser> {
  const { data } = await api.post<AuthResponse>('/api/auth/register', payload);
  return toAuthUser(data);
}

export async function login(payload: LoginPayload): Promise<AuthUser> {
  const { data } = await api.post<AuthResponse>('/api/auth/login', payload);
  return toAuthUser(data);
}
