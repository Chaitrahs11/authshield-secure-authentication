import { Component } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable, catchError, finalize, switchMap, tap, throwError } from 'rxjs';
import { AuthApiService, AuthView, DashboardOverview } from './auth-api.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [DatePipe, FormsModule],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent {
  view: AuthView = 'login';
  email = '';
  password = '';
  newPassword = '';
  code = '';
  busy = false;
  notice = '';
  isError = false;
  dashboard: DashboardOverview | null = null;

  constructor(private readonly authApi: AuthApiService) {}

  show(view: AuthView): void {
    this.view = view;
    this.notice = '';
    this.isError = false;
    this.password = '';
    this.newPassword = '';
    this.code = '';
  }

  login(): void {
    this.runRequest(
      () => this.authApi.login(this.email, this.password).pipe(
        tap(response => this.authApi.setToken(response.accessToken)),
        switchMap(() => this.authApi.getDashboardOverview()),
        catchError(error => {
          this.authApi.clearToken();
          return throwError(() => error);
        })
      ),
      response => {
        this.dashboard = response;
        this.view = 'dashboard';
        this.password = '';
        this.email = response.email;
        this.setNotice('Protected dashboard data loaded successfully.');
      }
    );
  }

  register(): void {
    this.runRequest(() => this.authApi.register(this.email, this.password), response => {
      this.view = 'verify';
      this.password = '';
      this.setNotice(response.message);
    });
  }

  verifyEmail(): void {
    this.runRequest(() => this.authApi.verifyEmail(this.email, this.code), response => {
      this.show('login');
      this.setNotice(response.message);
    });
  }

  resendVerification(): void {
    this.runRequest(() => this.authApi.resendVerification(this.email), response => this.setNotice(response.message));
  }

  requestReset(): void {
    this.runRequest(() => this.authApi.requestPasswordReset(this.email), response => {
      this.view = 'reset';
      this.setNotice(response.message);
    });
  }

  resetPassword(): void {
    this.runRequest(() => this.authApi.resetPassword(this.email, this.code, this.newPassword), response => {
      this.show('login');
      this.setNotice(response.message);
    });
  }

  logout(): void {
    this.authApi.clearToken();
    this.dashboard = null;
    this.email = '';
    this.show('login');
    this.setNotice('You have signed out.');
  }

  private runRequest<T>(request: () => Observable<T>, onSuccess: (value: T) => void): void {
    this.busy = true;
    this.notice = '';
    this.isError = false;
    request().pipe(finalize(() => this.busy = false)).subscribe({
      next: value => onSuccess(value),
      error: (error: HttpErrorResponse) => {
        this.isError = true;
        this.notice = error.error?.message ?? 'Could not reach AuthShield. Check that the backend is running.';
      }
    });
  }

  private setNotice(message: string): void {
    this.notice = message;
    this.isError = false;
  }
}
