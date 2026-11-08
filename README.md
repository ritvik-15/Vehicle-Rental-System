# Vehicle Rental Management System

Demo college project: Spring Boot 3 backend (REST API) + React (Vite) frontend.

## Prerequisites

- JDK 17+ and Maven (backend), Node.js 18+ (frontend)
- MySQL 8 server with a `vehicle_rental_db` database

## Configuration (backend)

All configuration is supplied through environment variables:

| Variable | Required | Purpose |
| --- | --- | --- |
| `JWT_SECRET` | **Yes** | JWT signing secret, minimum 32 bytes (HS256). The application **refuses to start** without it — there is no fallback secret. |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Yes (local-MySQL defaults exist) | Datasource connection |
| `CORS_ALLOWED_ORIGINS` | No (default `http://localhost:5173`) | Allowed browser origins |
| `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET` | No (empty = demo mode) | Razorpay gateway credentials |

### Running locally

```bash
# backend (from backend/)
JWT_SECRET="$(openssl rand -base64 48)" DB_PASSWORD='<mysql-password>' mvn spring-boot:run

# frontend (from frontend/)
npm install
npm run dev
```

`JWT_SECRET` can be any strong random value of at least 32 bytes; `openssl rand -base64 48` (or an equivalent generator) is a convenient way to create one. Never commit real secrets to the repository.

## Database schema

`backend/src/main/resources/schema.sql` is the source of truth and is applied manually (`spring.sql.init.mode=never`). It is idempotent: tables use `CREATE TABLE IF NOT EXISTS` and each index is created only when missing, so the script can safely be re-run against an existing database without dropping, truncating, or altering any existing table, index, or record.

## Provisioning the first admin

Admin accounts are provisioned by the opt-in startup bootstrap (`AdminBootstrap`). It is **disabled by default** and never modifies existing users — it only creates a brand-new `ADMIN` user when all of the following are supplied at startup:

```bash
ADMIN_BOOTSTRAP_ENABLED=true \
ADMIN_EMAIL='admin@example.com' \
ADMIN_PASSWORD='<strong-password>'
# optional: ADMIN_NAME='Administrator' ADMIN_PHONE='0000000000'
```

Safety properties: disabled unless explicitly enabled; requires both email and password; encodes the password before storing; skips creation if the email already exists; never promotes an existing customer and never weakens authorization. Set `ADMIN_BOOTSTRAP_ENABLED` only for initial provisioning (or when deliberately adding another admin), then unset it.

## Demo payment mode

With empty `RAZORPAY_KEY_ID`/`RAZORPAY_KEY_SECRET`, the booking flow offers a "Simulate Successful Payment (Demo)" button that records a demo payment without charging real money. Use "Proceed to Razorpay Payment" for real payments once credentials are configured.
