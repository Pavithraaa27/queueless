import { api } from './client';
import type { Business } from '../types';

export interface BusinessAnalytics {
  businessId: number;
  totalCheckInsToday: number;
  totalServedToday: number;
  noShowCountToday: number;
  avgServiceTimeSecondsToday: number | null;
  currentAvgServiceTimeSeconds: number;
}

export async function getAnalytics(businessId: number): Promise<BusinessAnalytics> {
  const { data } = await api.get<BusinessAnalytics>(`/api/businesses/${businessId}/analytics`);
  return data;
}

export async function getInsight(businessId: number): Promise<string> {
  const { data } = await api.get<{ insight: string }>(`/api/businesses/${businessId}/insight`);
  return data.insight;
}

export interface BusinessPayload {
  name: string;
  category: string;
  address: string;
  latitude?: number;
  longitude?: number;
}

export async function listBusinesses(category?: string): Promise<Business[]> {
  const { data } = await api.get<Business[]>('/api/businesses', { params: { category } });
  return data;
}

export async function getNearbyBusinesses(lat: number, lng: number, radiusKm = 10): Promise<Business[]> {
  const { data } = await api.get<Business[]>('/api/businesses/nearby', { params: { lat, lng, radiusKm } });
  return data;
}

export async function getBusiness(id: number): Promise<Business> {
  const { data } = await api.get<Business>(`/api/businesses/${id}`);
  return data;
}

export async function myBusinesses(): Promise<Business[]> {
  const { data } = await api.get<Business[]>('/api/businesses/mine');
  return data;
}

export async function createBusiness(payload: BusinessPayload): Promise<Business> {
  const { data } = await api.post<Business>('/api/businesses', payload);
  return data;
}

export async function setAcceptingCheckIns(id: number, accepting: boolean): Promise<Business> {
  const { data } = await api.patch<Business>(`/api/businesses/${id}/accepting`, null, {
    params: { accepting },
  });
  return data;
}
