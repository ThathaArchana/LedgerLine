# LedgerLine API

## Features

- Customer signup requests remain pending until an admin approves them; only active users can log in and receive JWT access tokens. Passwords are BCrypt-hashed.
- Email addresses are normalized and unique, so an existing email cannot register another user account.
- Password reset uses a single-use, 15-minute link sent by SMTP email; token hashes, not raw tokens, are stored in the database.
- Customers can create and view their own accounts. Admins can approve pending signups and view users, accounts, and transactions.
- Deposits and withdrawals are authenticated, lock the account row, prevent overdrafts, and create two balanced ledger lines.
- Money movement requires an `Idempotency-Key` header. Repeating the same request returns its original transaction instead of posting it again.
- PostgreSQL is supported for persistent runtime data. Local development defaults to in-memory H2, which is reset when the application stops. Tests use an isolated H2 database.
- Redis-backed login throttling is optional and can be enabled with `REDIS_ENABLED=true`; configure Redis authentication and TLS for deployed environments.
- The admin is initialized only when `ADMIN_EMAIL` and `ADMIN_PASSWORD` are provided. If that email already belongs to a customer, startup promotes it and sets its password to `ADMIN_PASSWORD`.

## Local Services

Run locally with the default in-memory H2 database:

```powershell
mvn spring-boot:run
```

The API health check is available at `http://localhost:8080/api/health`. To use PostgreSQL instead, create a database named `ledgerline` and set its connection details before starting the API:

```powershell
$env:DB_URL="jdbc:postgresql://localhost:5432/ledgerline"
$env:DB_USERNAME="ledgerline"
$env:DB_PASSWORD="your-database-password"
$env:DB_DRIVER="org.postgresql.Driver"
$env:DB_DIALECT="org.hibernate.dialect.PostgreSQLDialect"
$env:JWT_SECRET="replace-with-a-random-secret-at-least-32-characters-long"
$env:CORS_ALLOWED_ORIGINS="http://localhost:5173"
$env:ADMIN_EMAIL="admin@example.com"
$env:ADMIN_PASSWORD="use-a-unique-password-at-least-12-characters"
$env:SMTP_HOST="smtp.example.com"
$env:SMTP_PORT="587"
$env:SMTP_USERNAME="your-smtp-username"
$env:SMTP_PASSWORD="your-smtp-password"
$env:SMTP_FROM="no-reply@example.com"
$env:FRONTEND_URL="http://localhost:5173"
mvn spring-boot:run
```

SMTP is required for password-reset emails. Keep SMTP credentials in environment variables, not source control. The user-facing response does not reveal whether the requested email is registered.

To enable Redis-backed throttling, also configure `REDIS_ENABLED=true`, `REDIS_HOST`, `REDIS_PORT`, `REDIS_USERNAME`, `REDIS_PASSWORD`, and set `REDIS_SSL=true` when the Redis endpoint supports TLS. The application does not start Redis itself. Set `CORS_ALLOWED_ORIGINS` to a comma-separated list when the frontend is hosted outside localhost.

## API

- `POST /api/auth/signup` with `{ "name", "email", "password" }`
- `POST /api/auth/login` with `{ "email", "password" }`
- `POST /api/auth/forgot-password` with `{ "email" }`
- `POST /api/auth/reset-password` with `{ "token", "newPassword" }`
- `POST /api/accounts` with optional `{ "currency": "INR" }`
- `GET /api/accounts` and `GET /api/accounts/{id}`
- `POST /api/transactions/deposit` or `/withdraw` with `{ "accountId", "amount", "description" }` and an `Idempotency-Key` header
- `GET /api/transactions/{accountId}`
- Admin-only: `GET /api/admin/users`, `POST /api/admin/users/{id}/approve`, `GET /api/admin/accounts`, and `GET /api/admin/transactions`

Send the returned token on protected API requests as `Authorization: Bearer <token>`. Public signup always creates a `CUSTOMER`; admin privileges are assigned only by the configured bootstrap account.