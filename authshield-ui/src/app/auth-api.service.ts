import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';

export type AuthView = 'login' | 'register' | 'verify' | 'forgot' | 'reset' | 'dashboard';

interface MessageResponse {
  message: string;
}

interface TokenResponse {
  accessToken: string;
  tokenType: string;
  expiresInSeconds: number;
  email: string;
}

export interface DashboardOverview {
  email: string;
  accountStatus: string;
  emailVerified: boolean;
  authenticationMethod: string;
  accountCreatedAt: string;
  securityStatus: string;
  message: string;
  securityFeatures: string[];
}

@Injectable({ providedIn: 'root' })
export class AuthApiService {
  private readonly apiUrl = 'http://localhost:8080/api';
  private accessToken = '';

  constructor(private readonly http: HttpClient) {}

  register(email: string, password: string): Observable<MessageResponse> {
    return this.http.post<MessageResponse>(`${this.apiUrl}/auth/register`, { email, password });
  }

  verifyEmail(email: string, code: string): Observable<MessageResponse> {
    return this.http.post<MessageResponse>(`${this.apiUrl}/auth/verify-email`, { email, code });
  }

  resendVerification(email: string): Observable<MessageResponse> {
    return this.http.post<MessageResponse>(`${this.apiUrl}/auth/resend-verification`, { email });
  }

  login(email: string, password: string): Observable<TokenResponse> {
    return this.http.post<TokenResponse>(`${this.apiUrl}/auth/login`, { email, password });
  }

  requestPasswordReset(email: string): Observable<MessageResponse> {
    return this.http.post<MessageResponse>(`${this.apiUrl}/auth/forgot-password`, { email });
  }

  resetPassword(email: string, code: string, newPassword: string): Observable<MessageResponse> {
    return this.http.post<MessageResponse>(`${this.apiUrl}/auth/reset-password`, { email, code, newPassword });
  }

  getDashboardOverview(): Observable<DashboardOverview> {
    const headers = new HttpHeaders({ Authorization: `Bearer ${this.accessToken}` });
    return this.http.get<DashboardOverview>(`${this.apiUrl}/dashboard/overview`, { headers });
  }

  setToken(token: string): void {
    this.accessToken = token;
  }

  clearToken(): void {
    this.accessToken = '';
  }
}
