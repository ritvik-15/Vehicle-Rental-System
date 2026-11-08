# FINAL_DEMO_GUIDE.md

## Vehicle Rental Management System - College Demo Guide

**Version**: Final (2026-10-12)

---

## Quick Start

### Prerequisites
- Java 17+ (OpenJDK 17.0.20 recommended)
- Maven 3.10.0+
- Node.js 18+ (for frontend dev server)
- MySQL 8.0 with `vehicle_rental_db` database
- Environment variables (see below)

### Required Environment Variables (Backend)

Create a `.env` or export in shell before starting backend:

```bash
# REQUIRED - JWT signing secret (min 32 bytes, HS256)
# Generate: openssl rand -base64 48
export JWT_SECRET="your-generated-secret-here"

# Database (defaults work for local MySQL)
export DB_URL="jdbc:mysql://localhost:3306/vehicle_rental_db"
export DB_USERNAME="root"
export DB_PASSWORD="your-mysql-password"

# Optional
export CORS_ALLOWED_ORIGINS="http://localhost:5173"
export RAZORPAY_KEY_ID=""        # Empty = demo mode
export RAZORPAY_KEY_SECRET=""    # Empty = demo mode
export ADMIN_BOOTSTRAP_ENABLED="false"  # Set true + ADMIN_EMAIL/ADMIN_PASSWORD to create admin
```

**Note**: The running backend uses a process-scoped random `JWT_SECRET`. To preserve sessions on restart, you MUST use the SAME secret value.

---

## Starting the Application

### Option 1: Run Both (Development)

**Terminal 1 - Backend**:
```bash
cd backend
# Ensure env vars are set
mvn spring-boot:run
```
Backend starts on `http://localhost:8080`

**Terminal 2 - Frontend**:
```bash
cd frontend
npm install
npm run dev
```
Frontend starts on `http://localhost:5173`

### Option 2: Production Build (Using New Verified Artifact)

**Backend** (using pre-built verified artifact):
```bash
# Copy the verified artifact (built in isolated temp directory, 52 MB, all R1-R2 changes + phone validation included)
cp C:\temp\vehicle-rental-r1r2-build\backend\target\vehicle-rental-0.0.1-SNAPSHOT.jar backend\target\vehicle-rental-0.0.1-SNAPSHOT.jar

# Start with same JWT_SECRET to preserve sessions
export JWT_SECRET="your-same-secret-as-before"
java -jar backend/target/vehicle-rental-0.0.1-SNAPSHOT.jar
```
Backend starts on `http://localhost:8080`

**Frontend**:
```bash
cd frontend
npm install
npm run build
# Serve dist/ with any static server (nginx, vite preview, etc.)
npx vite preview --port 5173
```
Frontend starts on `http://localhost:5173`

---

## Demo Flows

### 1. Customer Journey (Fully Working - Verified)

#### A. Register & Login
1. Open `http://localhost:5173/register`
2. Fill: Full Name, Email, Phone (exactly 10 digits), Password (min 6 chars)
3. Click "Register" → redirects to login
4. Login with credentials → lands on Home

**Phone Validation** (new):
- `9876543210` — ✅ valid (exactly 10 digits)
- `987654321` — ❌ invalid: too short (9 digits)
- `98765432101` — ❌ invalid: too long (11 digits)
- `98abc43210` — ❌ invalid: contains letters
- `98765 43210` — ❌ invalid: contains space

#### B. Browse Vehicles
1. Home page shows hero + search filters + 12 vehicle cards
2. Test filters:
   - **Search**: Type "Thar" → shows 1 result
   - **Type**: Select "BIKE" → shows 4 bikes
   - **Fuel**: Select "DIESEL" → filters
   - **Sort**: "Price: low to high" / "Price: high to low"
3. Click "View details" on any card

#### C. Vehicle Details & Availability
1. On detail page: see specs, images, price (₹/day)
2. Set Pickup date/time (future) and Return date/time (after pickup)
3. Click "Check availability" → shows "Vehicle is available for the requested period" or conflict message
4. If available: "Continue to Booking" button appears

#### D. Booking
1. Click "Continue to Booking" → Booking page
2. Dates pre-filled from availability check
3. See price breakdown:
   - Daily rate × days = Subtotal
   - Tax (18%)
   - Security Deposit (20%)
   - **Total**
4. Click "Book Now" → Creates PENDING rental

#### E. Payment (Demo)
1. On booking confirmation or My Rentals: "Pay Now" button
4. Click → Simulates Razorpay payment (demo mode)
5. Rental status changes: PENDING → CONFIRMED
6. Payment record created with "DEMO SUCCESS" message

#### F. My Rentals
1. Navigate to "My Rentals" in top nav
2. See all rentals with full pricing breakdown
3. PENDING rentals with future pickup: "Cancel rental" button
4. Click cancel → confirms → status CANCELLED

#### G. Profile
1. Click profile name → "Profile"
2. Edit full name, phone (must be 10 digits), address
5. Change password (current + new)

#### H. Logout
1. Click "Logout" in top nav → returns to login page

---

### 2. Admin Journey (Requires Restart with Admin Bootstrap)

**CURRENT STATUS**: Admin endpoints exist in new artifact but NOT in running JAR (returns 401).

#### To Enable Admin Access for Demo:

**Option A: Use Admin Bootstrap (Recommended for Demo)**
```bash
# Stop backend (Ctrl+C on PID 22720)
# Replace JAR with new verified artifact:
cp C:\temp\vehicle-rental-r1r2-build\backend\target\vehicle-rental-0.0.1-SNAPSHOT.jar backend\target\vehicle-rental-0.0.1-SNAPSHOT.jar
# Restart with admin bootstrap (MUST use same JWT_SECRET to preserve sessions, OR accept session loss):
export ADMIN_BOOTSTRAP_ENABLED="true"
export ADMIN_EMAIL="admin@demo.com"
export ADMIN_PASSWORD="AdminPass123!"
export ADMIN_BOOTSTRAP_NAME="Demo Admin"
export ADMIN_BOOTSTRAP_PHONE="9999999999"
# Use SAME JWT_SECRET as before!
mvn spring-boot:run
```
Then login at `/login` with `admin@demo.com` / `AdminPass123!`

#### Option B: Recover Original JWT_SECRET
If you have the original secret used when the current admin was created, use it to restart with same secret.

#### Admin Pages (once logged in):
- **Dashboard** (`/admin`) - Statistics
- **Vehicles** (`/admin/vehicles`) - CRUD, images, activate/deactivate
- **Customers** (`/admin/customers`) - List, view details
- **Rentals** (`/admin/rentals`) - List, view, status changes
- **Payments** (`/admin/payments`) - List, view
- **Maintenance** (`/admin/maintenance`) - Create, status transitions
- **Reports** (`/admin/reports`) - Summary, revenue, rentals, vehicles, maintenance
- **Promo Codes** (`/admin/promos`) - Create, list, activate/deactivate

---

### 3. Maintenance Status Transitions (Backend Verified)

Valid transitions (enforced by `MaintenanceService`):
- SCHEDULED → IN_PROGRESS, COMPLETED, CANCELLED
- IN_PROGRESS → COMPLETED, CANCELLED
- COMPLETED → (terminal)
- CANCELLED → (terminal)
- Same status → REJECTED (400)

---

### 4. Promo Code Demo

**Backend API** (admin only):
- `GET /api/admin/promos` - List all
- `POST /api/admin/promos` - Create
  ```json
  {
    "code": "COLLEGE20",
    "discountType": "PERCENTAGE",
    "discountValue": 20,
    "minimumAmount": 1000,
    "maximumDiscount": 500,
    "startDate": "2026-10-12",
    "expiryDate": "2026-12-31",
    "usageLimit": 100,
    "status": "ACTIVE"
  }
  ```
- `PATCH /api/admin/promos/{code}/status` - Activate/Deactivate
  ```json
  { "status": "INACTIVE" }
  ```

**Customer Usage**: At booking, apply promo code → validated server-side (dates, min amount, usage limit, percentage ≤100%)

---

## New Backend Artifact (Ready for Deployment)

**Location**: `C:\temp\vehicle-rental-r1r2-build\backend\target\vehicle-rental-0.0.1-SNAPSHOT.jar`
- **Size**: 52,244,622 bytes (52 MB)
- **Built**: In isolated temp directory to avoid file lock on running JAR
- **Verified Contents**:
  - `VehicleRentalApplication.class` (with `@EnableScheduling`)
  - `RentalService.class` (with `sweepExpiredPendingRentals()` @5min)
  - `MaintenanceService.class` (with transition matrix)
  - `AdminPromoController.class` (with GET/POST/PATCH endpoints)
  - `RegisterRequest.class` (with phone `@Pattern`)
  - `ProfileUpdateRequest.class` (with phone `@Pattern`)
- **Tests**: 253/253 pass in temp build (including 7 new phone validation tests)
- **Running JAR**: Untouched (PID 22720 continues on original JAR)

**Deployment Steps** (requires approval):
1. Stop current backend (Ctrl+C on PID 22720)
2. Backup current JAR: `copy backend\target\vehicle-rental-0.0.1-SNAPSHOT.jar backend\target\vehicle-rental-0.0.1-SNAPSHOT.jar.backup`
3. Deploy new artifact: `copy C:\temp\vehicle-rental-r1r2-build\backend\target\vehicle-rental-0.0.1-SNAPSHOT.jar backend\target\vehicle-rental-0.0.1-SNAPSHOT.jar`
4. Restart with **same JWT_SECRET** (to preserve sessions) OR with admin bootstrap (accepts session loss)
5. Verify `/api/health` and admin endpoints

---

## Known Limitations for Demo

| Limitation | Workaround |
|------------|------------|
| No admin session | Use admin bootstrap (see above) |
| Prices show in API as `dailyRate` | Frontend displays correctly as ₹/day |
| Availability check needs real typing | Programmatic test tools don't update React state; real typing works |
| My Rentals initial load | Fixed via storage event listener; manual refresh works if needed |
| No isolated test DB | Integration tests blocked; unit tests cover logic |
| Real Razorpay | Demo mode only (no real charges) |

---

## Recovery / Troubleshooting

### Backend Won't Start
- Check `JWT_SECRET` is set (min 32 bytes)
- Check MySQL is running and `vehicle_rental_db` exists
- Check port 8080 free

### Frontend Can't Connect
- Check `VITE_API_BASE_URL` (default: `http://localhost:8080`)
- Check CORS: backend `CORS_ALLOWED_ORIGINS` includes `http://localhost:5173`

### Admin Login Fails
- JWT secret changed since admin created → use bootstrap or same secret
- Admin bootstrap disabled by default → enable with env vars

### My Rentals Stuck Loading
- Refresh page (fixed in latest code via storage event listener)
- Or clear sessionStorage and re-login

### Booking Fails
- Vehicle must have `dailyRate` > 0 (all 12 do)
- Pickup must be future, return after pickup
- Vehicle must be AVAILABLE and not in maintenance
- No overlapping confirmed bookings

### Phone Validation Fails
- Must be exactly 10 digits (e.g., `9876543210`)
- No letters, spaces, or special characters
- Not 9 digits, not 11 digits

---

## Database Notes

- **Live DB**: `vehicle_rental_db` (production data)
- **Test DB**: Not configured (integration tests blocked)
- **Schema**: `backend/src/main/resources/schema.sql` (idempotent)
- **QA Test Records** (do not delete):
  - User: 2385 (qa.test.xxx@example.com)
  - Rentals: 1545 (CANCELLED), 1546 (CANCELLED), 1547 (CANCELLED), 1548 (CANCELLED)

---

## Project Structure Quick Reference

```
backend/
├── src/main/java/com/vehicle/rental/
│   ├── VehicleRentalApplication.java          # @EnableScheduling
│   ├── controller/
│   │   ├── AdminPromoController.java          # NEW R2
│   │   ├── AdminVehicleController.java
│   │   ├── AdminMaintenanceController.java
│   │   ├── VehicleController.java
│   │   ├── RentalController.java
│   │   ├── PromoController.java
│   │   ├── PaymentController.java
│   │   ├── AuthController.java
│   │   └── UserAccountController.java
│   ├── service/
│   │   ├── RentalService.java                 # sweepExpiredPendingRentals (R1-A)
│   │   ├── MaintenanceService.java            # Transition matrix (R1-B)
│   │   ├── AdminPromoService.java             # NEW R2
│   │   ├── VehicleService.java
│   │   ├── PromoService.java
│   │   ├── AuthService.java
│   │   └── UserAccountService.java
│   └── dto/
│       ├── RegisterRequest.java               # Phone @Pattern (NEW)
│       ├── ProfileUpdateRequest.java          # Phone @Pattern (NEW)
│       ├── PromoCodeAdminCreateRequest.java   # NEW R2
│       ├── PromoCodeAdminStatusRequest.java   # NEW R2
│       └── PromoCodeAdminResponse.java        # NEW R2

frontend/
├── src/
│   ├── context/AuthContext.jsx                # FIXED: storage event listener
│   ├── services/
│   │   ├── adminPromoService.js               # NEW Phase 13A
│   │   ├── vehicleService.js
│   │   ├── api.js
│   │   └── ...
│   ├── pages/admin/
│   │   ├── PromoCodesPage.jsx                 # NEW Phase 13A
│   │   ├── MaintenancePage.jsx
│   │   └── ...
│   ├── pages/customer/
│   │   ├── HomePage.jsx
│   │   ├── VehicleDetailsPage.jsx
│   │   ├── BookingPage.jsx
│   │   ├── MyRentalsPage.jsx                  # FIXED: storage sync
│   │   ├── ProfilePage.jsx                    # Phone validation (NEW)
│   │   └── RegisterPage.jsx                   # Phone validation (NEW)
│   └── layouts/
│       ├── AdminLayout.jsx                    # UPDATED: Promo Codes nav
│       └── CustomerLayout.jsx
```

---

## Contact / Escalation

For issues during demo:
1. Check browser console (F12) for errors
2. Check backend logs for exceptions
3. Verify environment variables
4. Verify database connectivity

**Key Files to Inspect**:
- `backend/src/main/resources/application.properties`
- `frontend/src/services/api.js` (base URL, auth handling)
- `backend/src/main/java/com/vehicle/rental/security/SecurityConfig.java` (role config)

---

**End of Demo Guide**