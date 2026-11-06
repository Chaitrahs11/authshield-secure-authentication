# AuthShield Architecture

This document describes the current AuthShield system architecture and authentication flows. It reflects the code as implemented today and calls out relevant limitations where the implementation differs from a production design.

## 1. System Overview

AuthShield is a full-stack authentication and account-security application. Users can register with an email and password, verify their email using a one-time code, sign in to receive a JWT, access a protected account dashboard, and request a password reset.

The frontend is an Angular single-page application. The backend is a Java 21 Spring Boot REST API. PostgreSQL stores account and OTP challenge records. Mailtrap's Sandbox HTTP API captures verification and reset messages during development.

## 2. High-Level Architecture

```text
+----------------------+       JSON / Bearer JWT       +--------------------------+
| Angular application  | ----------------------------> | Spring Boot REST API     |
| localhost:4200       |                                | localhost:8080           |
+----------------------+                                +-------------+------------+
                                                                      |
                                            +-------------------------+------------------+
                                            |                         |                  |
                                            v                         v                  v
                                     Spring Security          Service layer       Spring Data JPA
                                     JWT filter               auth/OTP/JWT/       repositories
                                     access rules             dashboard/email          |
                                            |                         |                  v
                                            |                         |          +---------------+
                                            |                         +--------> | PostgreSQL    |
                                            |                                    +---------------+
                                            |                         |
                                            |                         | HTTPS JSON + API token
                                            |                         v
                                            |                 +----------------------+
                                            +---------------->| Mailtrap Sandbox API |
                                                              +----------------------+
```

The browser communicates only with the Spring Boot API. It does not connect directly to PostgreSQL or send email. Spring Boot makes outbound HTTPS requests to Mailtrap for OTP messages.

## 3. Frontend Architecture

The Angular app is currently organized around a standalone root component, rather than distinct routed pages:

- `AppComponent` tracks the current view (`login`, `register`, `verify`, `forgot`, `reset`, or `dashboard`) and manages form submissions and transitions.
- `app.component.html` conditionally renders the forms or the dashboard.
- `app.component.css` styles the auth split-screen and the single-column authenticated dashboard layout.
- `AuthApiService` sends HTTP requests to the backend and holds the access token in memory for the current page session.
- `app.config.ts` provides Angular `HttpClient`.
- `app.routes.ts` currently has no routes configured; view changes happen through component state, not URL navigation.

After login, Angular stores the returned JWT in memory and requests the protected dashboard endpoint. It displays the dashboard only after the backend returns a successful response. Sign-out clears the in-memory token and dashboard data. Reloading the page clears that in-memory session and requires the user to sign in again.

## 4. Backend Architecture

The backend follows a layered structure:

| Layer | Responsibility | Main classes |
| --- | --- | --- |
| Controller | Maps HTTP routes to service calls and selects response status | `AuthController`, `DashboardController` |
| Service | Implements account, OTP, login, reset, JWT, email, and dashboard rules | `AuthService`, `DashboardService`, `JwtService`, `OtpEmailService` |
| Repository | Reads/writes database entities through Spring Data JPA | `UserRepository`, `OtpChallengeRepository` |
| Entity | Maps Java objects to relational tables | `AppUser`, `OtpChallenge` |
| DTO | Validates incoming fields and defines response bodies | `AuthRequests`, `AuthResponses` |
| Common | Maps API/domain exceptions to HTTP responses | `ApiException`, `EmailDeliveryException`, `ApiExceptionHandler` |

Flyway owns schema creation and versioning. Hibernate is configured to validate the schema at startup rather than create or modify tables automatically.

## 5. Authentication Flow

### Registration

1. Angular posts an email and password to `POST /api/auth/register`.
2. Bean Validation checks the email and password length. The service trims and lowercases the email and rejects duplicates.
3. Spring Security's BCrypt `PasswordEncoder` hashes the password; the plaintext password is not stored.
4. A new user is saved as unverified.
5. The backend generates a cryptographically random six-digit OTP, hashes it with BCrypt, and persists a `VERIFY_EMAIL` challenge with its expiry and resend time.
6. `OtpEmailService` sends the original code to Mailtrap over HTTPS.
7. On success, the API responds `201 Created` with a message, not the OTP.

### Email OTP verification

1. Angular posts the normalized account email and six-digit code to `POST /api/auth/verify-email`.
2. The service locks the user row while checking the latest unconsumed verification challenge.
3. The service rejects missing, expired, consumed, or exhausted challenges and compares the submitted code to the stored BCrypt hash.
4. A valid challenge is consumed and the user is marked verified.
5. Success returns `200 OK`; an invalid or expired code returns `400 Bad Request`.

### Login

1. Angular posts email/password to `POST /api/auth/login`.
2. The service looks up the normalized email and compares the password with its BCrypt hash.
3. Invalid credentials return `401 Unauthorized`; an unverified account returns `403 Forbidden`.
4. On success, `JwtService` creates a signed access token whose subject is the account email and whose expiry is 15 minutes by default.
5. The response includes the token, token type (`Bearer`), expiry seconds, and email.
6. Angular uses the JWT for the protected dashboard request.

## 6. JWT Security Flow

For protected requests, the client sends:

```http
Authorization: Bearer <access-token>
```

`JwtAuthenticationFilter` runs once per request before Spring's username/password filter. It reads the Bearer token, asks `JwtService` to verify its signature and expiry, extracts the token subject, and places an authenticated principal in Spring's `SecurityContext`. Spring Security then applies the configured access rules. Unauthenticated protected requests receive `401 Unauthorized`.

The dashboard obtains the email from Spring's `Authentication` principal, not from an email value supplied by the browser. `DashboardService` uses that identity to load the corresponding account row and construct the response.

JWT signing uses an HMAC key loaded from `JWT_SECRET_BASE64`. The tracked `application.properties` currently contains a development fallback so the app can run locally. It is public source code, not a production secret: deployments must supply a newly generated, strong key through the environment or a secret manager, and must never commit the real key.

## 7. Forgot Password Flow

1. Angular posts an email to `POST /api/auth/forgot-password`.
2. The backend returns a generic response regardless of whether an eligible verified account exists, avoiding direct email enumeration through the response.
3. For an eligible account, the backend creates a `RESET_PASSWORD` OTP challenge and sends the code through Mailtrap.
4. Angular submits email, code, and new password to `POST /api/auth/reset-password`.
5. The service checks the active reset challenge, consumes it, BCrypt-hashes the new password, and updates the user.
6. Success returns `200 OK`; an invalid or expired code returns `400 Bad Request`.

Password reset uses the same expiry, resend cooldown, send window, and failed-attempt settings as email verification, while keeping the challenge purpose separate.

## 8. Database Design

The Flyway migration `V1__create_auth_tables.sql` creates two tables.

### `app_users`

- `id`: generated primary key
- `email`: unique normalized email address
- `password_hash`: BCrypt password hash
- `is_verified`: email verification state
- `created_at`, `updated_at`: account timestamps

### `otp_challenges`

- `id`: generated primary key
- `user_id`: foreign key to `app_users`, with cascade delete
- `otp_hash`: BCrypt hash of the code
- `purpose`: `VERIFY_EMAIL` or `RESET_PASSWORD`
- `expires_at`: challenge expiry
- `failed_attempts`: incorrect submission count
- `resend_available_at`: cooldown boundary
- `consumed_at`: records challenge consumption/replacement/lockout
- `created_at`: creation time used for rate-limit counting

An index on user, purpose, and creation time supports recent challenge lookups and send-window checks. No separate authentication event/audit table currently exists.

## 9. Email Integration: Mailtrap Sandbox HTTP API

SMTP STARTTLS negotiation timed out in the development environment even though TCP connections to the SMTP port succeeded. HTTPS requests to Mailtrap worked, so `OtpEmailService` uses Java's built-in `HttpClient` to send JSON to the Mailtrap Sandbox API.

The request includes sender, recipient, subject, text body, and category fields. It is authenticated with an API token in the `Authorization: Bearer` header. The API URL, token, sender email, and sender name are configurable through `authshield.mailtrap.*` properties and corresponding environment-variable overrides. The local profile file is ignored by Git and should hold local-only credentials; never commit or share the API token.

Mailtrap **Sandbox captures messages in its inbox**. It does not deliver them to real Gmail inboxes. Real delivery requires a transactional email service configuration, an approved sender identity, and the appropriate production API credentials.

Email delivery is performed within the registration/reset service transaction. Therefore an API failure can roll back the database work performed by that request. A production-grade implementation should consider an outbox and retry mechanism so external email availability is decoupled from database commits.

## 10. Security Measures

- **Password protection:** passwords are stored as BCrypt hashes.
- **OTP protection:** codes are generated on the backend, BCrypt-hashed at rest, expire, have resend cooldowns and send limits, track failures, and are consumed to prevent reuse.
- **JWT authentication:** protected requests require a valid signature and unexpired token.
- **Stateless API:** Spring Security does not create an HTTP session.
- **Input validation:** email, password, and six-digit OTP formats are checked at the API boundary.
- **CORS:** the configured Angular origin is allowed to call the API with JSON and the Authorization header.
- **Secret handling:** database and Mailtrap credentials should be supplied locally and excluded from version control. Replace the development JWT secret fallback before deployment.
- **Email enumeration:** password-reset requests use a generic response.

### Current security caveat

The current Spring Security rules permit `/api/auth/**` as a group. That makes the existing `GET /api/auth/me` endpoint public under the present matcher, despite its controller method accepting an `Authentication` argument. The supported protected-data test is `GET /api/dashboard/overview`, which is covered by the authenticated catch-all rule. Before relying on `/api/auth/me`, narrow the public matchers to specific public routes and protect `/api/auth/me` explicitly.

### Before describing the application as production-secured

- Replace the broad `/api/auth/**` public matcher with an allowlist of public authentication routes. Keep `/api/auth/me` and dashboard/account routes authenticated, then verify requests without a token return `401`.
- Remove the tracked development JWT secret fallback for deployed environments. Require a deployment-provided secret and keep it in environment configuration or a secret manager, never in Git.
- Keep using the authenticated principal as the identity source for dashboard data. Do not accept an email from the request body or query string to choose which account the protected dashboard returns.

## API Summary

| Method | Route | Access | Purpose |
| --- | --- | --- | --- |
| `POST` | `/api/auth/register` | Public | Create an unverified account and send verification OTP |
| `POST` | `/api/auth/verify-email` | Public | Verify email OTP and activate account |
| `POST` | `/api/auth/resend-verification` | Public | Issue a replacement verification OTP |
| `POST` | `/api/auth/login` | Public | Validate credentials and issue JWT |
| `POST` | `/api/auth/forgot-password` | Public | Request reset OTP with generic response |
| `POST` | `/api/auth/reset-password` | Public | Verify reset OTP and change password |
| `GET` | `/api/dashboard/overview` | JWT required | Return the authenticated user's security overview |
| `GET` | `/api/auth/me` | Public under current matcher | Existing profile route; matcher should be narrowed |
