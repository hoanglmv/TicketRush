export interface User {
  id: number;
  username: string;
  email: string;
  fullName: string;
  role: 'ROLE_ADMIN' | 'ROLE_CUSTOMER';
  gender?: 'MALE' | 'FEMALE' | 'OTHER';
  dateOfBirth?: string;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  userId: number;
  username: string;
  email: string;
  fullName: string;
  role: 'ROLE_ADMIN' | 'ROLE_CUSTOMER';
  gender?: 'MALE' | 'FEMALE' | 'OTHER';
  dateOfBirth?: string;
}

export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
  timestamp: string;
}

export interface EventResponse {
  id: number;
  name: string;
  description: string;
  venue: string;
  address: string;
  bannerUrl: string;
  eventDate: string;
  saleStartTime: string;
  saleEndTime: string;
  status: 'DRAFT' | 'PUBLISHED' | 'ON_SALE' | 'COMPLETED' | 'CANCELLED';
  queueEnabled: boolean;
  queueBatchSize: number;
  createdAt: string;
  category?: string;
  city?: string;
  zones: ZoneResponse[];
  totalSeats: number;
  availableSeats: number;
  soldSeats: number;
  hot: boolean;
  images: string[];
}

export interface ZoneResponse {
  id: number;
  eventId: number;
  name: string;
  color: string;
  price: number;
  totalRows: number;
  seatsPerRow: number;
  sortOrder: number;
  availableSeats: number;
  totalSeats: number;
}

export interface SeatResponse {
  id: number;
  zoneId: number;
  zoneName: string;
  zoneColor: string;
  rowNumber: number;
  colNumber: number;
  label: string;
  status: 'AVAILABLE' | 'LOCKED' | 'SOLD';
  price: number;
}

export interface SeatStatusUpdate {
  seatId: number;
  label: string;
  zoneId: number;
  status: 'AVAILABLE' | 'LOCKED' | 'SOLD';
  timestamp: string;
}

export interface TicketResponse {
  id: number;
  userId: number;
  username: string;
  eventId: number;
  eventName: string;
  venue: string;
  eventDate: string;
  seatId: number;
  seatLabel: string;
  zoneName: string;
  zoneColor: string;
  status: 'PENDING_PAYMENT' | 'PAID' | 'CANCELLED' | 'EXPIRED';
  price: number;
  qrCode: string;
  createdAt: string;
  paidAt: string;
  expiredAt: string;
  isResale: boolean;
  resalePrice: number;
}

export interface QueueStatusResponse {
  eventId: number;
  userId: number;
  position: number | null;
  totalInQueue: number;
  accessToken: string | null;
  hasAccess: boolean;
  message: string;
}

export interface DashboardStats {
  totalEvents: number;
  totalRevenue: number;
  totalUsers: number;
}

export interface EventStats {
  totalSeats: number;
  availableSeats: number;
  lockedSeats: number;
  soldSeats: number;
  occupancyRate: number;
  revenue: number;
}

export interface Demographics {
  gender: Record<string, number>;
  ageGroups: Record<string, number>;
  totalAttendees: number;
}

export interface ChatbotEventCard {
  id: number;
  name: string;
  venue: string;
  city: string;
  eventDate: string;
  bannerUrl?: string;
  minPrice?: number;
  status: string;
  isHot: boolean;
}

export interface ChatbotResponse {
  reply: string;
  events?: ChatbotEventCard[];
  suggestions?: string[];
  actionType?: string;
  targetEventId?: number;
}
