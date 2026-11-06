# AuthShield

A full-stack authentication and account-security application built with Angular, Spring Boot, and PostgreSQL. AuthShield supports account registration, email OTP verification, JWT login, protected account data, and password recovery.

## Features

- Register and sign in with email and password
- Verify email using a time-limited OTP
- Resend codes with cooldown and attempt limits
- Protect passwords with BCrypt hashing
- Authenticate protected APIs with JWT Bearer tokens
- View a personal security dashboard after signing in
- Request a password-reset code and set a new password

## Technology

- **Frontend:** Angular, TypeScript, HTML, CSS
- **Backend:** Java 21, Spring Boot, Spring Security, Spring Data JPA
- **Database:** PostgreSQL with Flyway migrations
- **Email testing:** Mailtrap Sandbox HTTP API

## Run Locally

Prerequisites: Java 21+, Maven, Node.js/npm, and PostgreSQL.

1. Create a PostgreSQL database named `authShield_db`.
2. Set `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` for your local PostgreSQL connection. Add your Mailtrap Sandbox API token to `authShield/src/main/resources/application-local.properties`. That local file is excluded from Git; never commit credentials.
3. Start the backend:

   ```powershell
   cd authShield
   mvn spring-boot:run
   ```

4. In another terminal, start the Angular app:

   ```powershell
   cd authshield-ui
   npm install
   npm start
   ```

5. Open [http://localhost:4200](http://localhost:4200).

Flyway creates the database tables when the backend starts. Mailtrap Sandbox captures verification and reset emails in its inbox; Sandbox messages are not delivered to Gmail.

## Main API Routes

| Method | Route | Access |
| --- | --- | --- |
| `POST` | `/api/auth/register` | Public |
| `POST` | `/api/auth/verify-email` | Public |
| `POST` | `/api/auth/resend-verification` | Public |
| `POST` | `/api/auth/login` | Public |
| `POST` | `/api/auth/forgot-password` | Public |
| `POST` | `/api/auth/reset-password` | Public |
| `GET` | `/api/dashboard/overview` | JWT required |

## Project Structure

- `authShield/` — Spring Boot backend
- `authshield-ui/` — Angular frontend

For detailed API flows, security behavior, database design, and Mailtrap configuration, see [IMPLEMENTATION.md](IMPLEMENTATION.md).
