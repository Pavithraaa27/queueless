import { api } from './client';
import type { Ticket } from '../types';

export async function checkIn(businessId: number): Promise<Ticket> {
  const { data } = await api.post<Ticket>(`/api/queue/${businessId}/check-in`);
  return data;
}

export async function callNext(businessId: number): Promise<Ticket | null> {
  const { data, status } = await api.post<Ticket>(`/api/queue/${businessId}/call-next`);
  return status === 204 ? null : data;
}

export async function cancelTicket(ticketId: number): Promise<Ticket> {
  const { data } = await api.delete<Ticket>(`/api/queue/tickets/${ticketId}`);
  return data;
}

export async function getSnapshot(businessId: number): Promise<Ticket[]> {
  const { data } = await api.get<Ticket[]>(`/api/queue/${businessId}/snapshot`);
  return data;
}
