export type Role = 'CUSTOMER' | 'BUSINESS_OWNER' | 'ADMIN';

export interface AuthUser {
  token: string;
  userId: number;
  fullName: string;
  email: string;
  role: Role;
}

export interface Business {
  id: number;
  name: string;
  category: string;
  address: string;
  latitude: number | null;
  longitude: number | null;
  avgServiceTimeSeconds: number;
  acceptingCheckIns: boolean;
  currentQueueLength: number;
  distanceKm: number | null;
}

export type TicketStatus = 'WAITING' | 'IN_SERVICE' | 'COMPLETED' | 'CANCELLED' | 'NO_SHOW';

export interface Ticket {
  id: number;
  businessId: number;
  businessName: string;
  customerId: number;
  customerName: string;
  status: TicketStatus;
  queuePosition: number | null;
  estimatedWaitSeconds: number | null;
  checkedInAt: string;
}
