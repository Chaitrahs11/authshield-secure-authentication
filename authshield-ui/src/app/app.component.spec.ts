import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { AppComponent } from './app.component';
import { AuthApiService, DashboardOverview } from './auth-api.service';

describe('AppComponent authentication flows', () => {
  let authApi: jasmine.SpyObj<AuthApiService>;

  const dashboard: DashboardOverview = {
    email: 'person@example.com',
    accountStatus: 'VERIFIED',
    emailVerified: true,
    authenticationMethod: 'JWT',
    accountCreatedAt: '2026-01-15T10:00:00Z',
    securityStatus: 'PROTECTED',
    message: 'Your account is protected.',
    securityFeatures: ['BCrypt password hashing', 'Email verification']
  };

  beforeEach(async () => {
    authApi = jasmine.createSpyObj<AuthApiService>('AuthApiService', [
      'register',
      'login',
      'getDashboardOverview',
      'verifyEmail',
      'resendVerification',
      'requestPasswordReset',
      'resetPassword',
      'setToken',
      'clearToken'
    ]);

    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [{ provide: AuthApiService, useValue: authApi }]
    }).compileComponents();
  });

  it('starts on the sign-in form', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();

    expect(fixture.componentInstance.view).toBe('login');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Sign in to your account');
  });

  it('moves to OTP verification after registration succeeds', () => {
    authApi.register.and.returnValue(of({ message: 'Check your email for the code.' }));
    const fixture = TestBed.createComponent(AppComponent);
    const component = fixture.componentInstance;
    component.email = 'person@example.com';
    component.password = 'long-example-password';

    component.register();

    expect(authApi.register).toHaveBeenCalledWith('person@example.com', 'long-example-password');
    expect(component.view).toBe('verify');
    expect(component.password).toBe('');
    expect(component.notice).toBe('Check your email for the code.');
  });

  it('loads the protected dashboard after login succeeds', () => {
    authApi.login.and.returnValue(of({
      accessToken: 'test.jwt.token',
      tokenType: 'Bearer',
      expiresInSeconds: 900,
      email: 'person@example.com'
    }));
    authApi.getDashboardOverview.and.returnValue(of(dashboard));
    const fixture = TestBed.createComponent(AppComponent);
    const component = fixture.componentInstance;
    component.email = 'person@example.com';
    component.password = 'long-example-password';

    component.login();
    fixture.detectChanges();

    expect(authApi.setToken).toHaveBeenCalledWith('test.jwt.token');
    expect(authApi.getDashboardOverview).toHaveBeenCalled();
    expect(component.view).toBe('dashboard');
    expect(component.dashboard).toEqual(dashboard);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Welcome back!');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('person@example.com');
  });

  it('clears the token and shows an error when the protected dashboard request fails', () => {
    authApi.login.and.returnValue(of({
      accessToken: 'test.jwt.token',
      tokenType: 'Bearer',
      expiresInSeconds: 900,
      email: 'person@example.com'
    }));
    authApi.getDashboardOverview.and.returnValue(throwError(() => new HttpErrorResponse({
      status: 401,
      error: { message: 'Unauthorized' }
    })));
    const fixture = TestBed.createComponent(AppComponent);
    const component = fixture.componentInstance;
    component.email = 'person@example.com';
    component.password = 'long-example-password';

    component.login();

    expect(authApi.clearToken).toHaveBeenCalled();
    expect(component.view).toBe('login');
    expect(component.dashboard).toBeNull();
    expect(component.isError).toBeTrue();
  });

  it('clears dashboard data and signs out', () => {
    const fixture = TestBed.createComponent(AppComponent);
    const component = fixture.componentInstance;
    component.view = 'dashboard';
    component.dashboard = dashboard;
    component.email = dashboard.email;

    component.logout();

    expect(authApi.clearToken).toHaveBeenCalled();
    expect(component.view).toBe('login');
    expect(component.dashboard).toBeNull();
    expect(component.email).toBe('');
  });
});
