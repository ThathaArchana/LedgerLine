# LedgerLine

LedgerLine is a secure money movement and verifiable ledger application
for the Snovasys Fresher Hiring Challenge 2026.

## Live Demo & Video Walkthrough

- **Demo Video (YouTube):** [https://youtu.be/7P7nAbpjxaU](https://youtu.be/7P7nAbpjxaU)
- **Live Frontend (Render):** [https://ledgerline-frontend-wwn8.onrender.com](https://ledgerline-frontend-wwn8.onrender.com)
- **Live API Health Check:** [https://ledgerline-api-1vid.onrender.com/api/health](https://ledgerline-api-1vid.onrender.com/api/health)

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

## Deploy on Render

### Option 1: Render Blueprint (`render.yaml`)

1. Push the repository to GitHub and open the Render Dashboard -> **New** -> **Blueprint**.
2. Select the repository (`render.yaml` at the root provisions `ledgerline-db` PostgreSQL, `ledgerline-api` Docker Web Service, and `ledgerline-frontend` Static Site).
3. When prompted for secret environment variables on `ledgerline-api`, provide:
   - `ADMIN_EMAIL`: administrator sign-in email
   - `ADMIN_PASSWORD`: strong password (**at least 12 characters**)
4. Click **Apply**. Render automatically links the PostgreSQL connection string, generates a secure `JWT_SECRET`, sets `CORS_ALLOWED_ORIGINS` and `FRONTEND_URL` to the frontend's Render URL, and sets `VITE_API_URL` on the frontend build.

### Option 2: Manual Render Services

1. **PostgreSQL (`New -> Postgres`)**: Create a database (e.g. `ledgerline`) and copy its **Internal Database URL**.
2. **Backend (`New -> Web Service`)**:
   - **Runtime**: `Docker`
   - **Root Directory**: `backend`
   - **Health Check Path**: `/api/health`
   - **Environment Variables**:
     - `DATABASE_URL`: paste Render's **Internal Database URL** (`postgresql://...`) — or set `DB_URL=jdbc:postgresql://<host>:5432/<db>`, `DB_USERNAME`, `DB_PASSWORD`
     - `JWT_SECRET`: random secret **at least 32 characters**
     - `ADMIN_EMAIL`: admin email
     - `ADMIN_PASSWORD`: admin password (**at least 12 characters**)
     - `CORS_ALLOWED_ORIGINS`: your frontend Render URL (e.g. `https://ledgerline-frontend.onrender.com`)
     - `FRONTEND_URL`: your frontend Render URL (e.g. `https://ledgerline-frontend.onrender.com`)
     - *(Optional)* `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_FROM` for password-reset emails (note: Render Free instances block outbound SMTP ports 25/465/587; use a paid instance or port `2525` if supported by your SMTP provider)
     - *(Optional)* `REDIS_ENABLED=true`, `REDIS_HOST`, `REDIS_PORT`, `REDIS_USERNAME`, `REDIS_PASSWORD`, `REDIS_SSL` for Redis login rate limiting
3. **Frontend (`New -> Static Site`)**:
   - **Root Directory**: `frontend`
   - **Build Command**: `npm ci && npm run build`
   - **Publish Directory**: `dist`
   - **Environment Variables**:
     - `NODE_VERSION`: `22` (Vite 7 requires Node 20.19+ or 22.12+)
     - `VITE_API_URL`: your backend Render URL (e.g. `https://ledgerline-api.onrender.com/api`)
   - **Redirects/Rewrites**: Source `/*` -> Destination `/index.html` (`Rewrite`)

## Current limitations

- The frontend supports real account and transaction API data. Analytics and
  payment-card management are not implemented.
- Deposits and withdrawals post entries to the app's ledger; they do not move
  money through an external bank or payment network.
- Local development uses in-memory H2; data is lost when the backend stops.
  Configure PostgreSQL as described in [backend/README.md](./backend/README.md)
  before using the app for persistent data.

## Development log

See [PROMPTS.md](./PROMPTS.md) for dated entries about actual AI-assisted
development work and corrections.
