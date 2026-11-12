# LedgerLine

LedgerLine is a secure money movement and verifiable ledger application
for the Snovasys Fresher Hiring Challenge 2026.

## Project status

The project includes a React/Vite frontend and a Spring Boot API. The backend
implements customer signup with admin approval, login with JWTs, account
creation and access control, deposits and withdrawals with idempotency keys,
balanced ledger entries, password reset by email, and admin
user/account/transaction endpoints. The frontend includes signup, login, an
admin approval panel, and live account and transaction views.

## Technology

- Frontend: React and Vite
- Backend: Java 21 and Spring Boot
- Database: in-memory H2 by default for local development; PostgreSQL is supported
- Backend build: Maven

## Prerequisites

- Node.js 20.19+ and npm
- Java 21
- Maven 3.6.3+

## Run the frontend

Start the backend first in one terminal. From `backend/`, configure the first
admin account and run the API:

```powershell
$env:ADMIN_EMAIL="admin@example.com"
$env:ADMIN_PASSWORD="choose-a-unique-password-at-least-12-characters"
mvn spring-boot:run
```

Sign in to the frontend with these admin credentials to approve signup requests.
New users cannot sign in until approved, and an email can only be registered
once.

In another terminal, from `frontend/`:

```shell
npm install
npm run dev
```

Open the Vite URL printed in the terminal (normally `http://localhost:5173`).
Check backend and database connectivity at `http://localhost:8080/api/health`.
Administrators use the same sign-in page as other users. After signing in with
the backend's configured `ADMIN_EMAIL` and `ADMIN_PASSWORD`, the admin approvals
tab appears in the dashboard.
The default H2 database is in memory and is cleared when the backend stops.
For persistent PostgreSQL setup and environment variables, see
[backend/README.md](./backend/README.md).

## Build and test the backend

From `backend/`:

```shell
mvn package
```

The backend listens on port `8080` by default. Set the `PORT` environment
variable to use another port. Redis-backed login throttling is optional and
disabled by default.

## Current limitations

- The frontend supports real account and transaction API data. Analytics and
  payment-card management are not implemented.
- Deposits and withdrawals post entries to the app's ledger; they do not move
  money through an external bank or payment network.
- Local development uses in-memory H2; data is lost when the backend stops.
  Configure PostgreSQL as described in [backend/README.md](./backend/README.md)
  before using the app for persistent data.
- The backend has no deployment configuration yet; production use still needs
  managed database credentials, a strong JWT secret, HTTPS, and operational
  monitoring.

## Development log

See [PROMPTS.md](./PROMPTS.md) for dated entries about actual AI-assisted
development work and corrections.
"# LedgerLine" 
