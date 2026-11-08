import { useEffect, useRef, useState } from 'react'
import { api } from '../../services/api.js'
import ErrorAlert from '../../components/ErrorAlert.jsx'

// Phase 10 Step 10 admin reports.
// Exact backend contracts (verified in AdminReportController/ReportService —
// five read-only GETs, ADMIN-only, NO request parameters, no pagination,
// no date filters; fixed response shapes with zero-defaults):
//   GET /api/admin/reports/rentals      -> { totalRentals, pendingCount,
//          confirmedCount, activeCount, completedCount, cancelledCount }
//          COUNT grouped by stored RentalStatus.
//   GET /api/admin/reports/revenue      -> { totalRevenue, successCount,
//          pendingCount, failedCount, cancelledCount, refundedCount,
//          totalTransactions }
//          COUNT+SUM(amount) grouped by PaymentStatus; totalRevenue is
//          strictly the SUCCESS row — never rental totals.
//   GET /api/admin/reports/vehicles     -> { totalVehicles, availableCount,
//          rentedCount, maintenanceCount, inactiveCount, popularVehicles:
//          [{ vehicleId, brand, model, vehicleType, rentalCount }] }
//          status snapshot + per-vehicle count of PENDING/CONFIRMED/ACTIVE/
//          COMPLETED rentals (CANCELLED excluded, zero-count vehicles
//          omitted, rentalCount DESC then vehicleId ASC).
//   GET /api/admin/reports/maintenance  -> { totalRecords, scheduledCount,
//          inProgressCount, completedCount, cancelledCount, totalCost,
//          averageCost }
//          COUNT+SUM(cost) grouped by MaintenanceStatus; totalCost sums
//          every status row; averageCost = totalCost / totalRecords
//          (2 dp HALF_UP, 0.00 when no records).
//
// NOT requested here: GET /api/admin/reports/summary — the Admin Dashboard
// already consumes it, and its six headline figures are repeated nowhere on
// this page (no redundant request for the same information). There is no
// customer report endpoint; totalCustomers exists only on /summary, so no
// customer section is shown here.
//
// Intentionally absent (unsupported by the backend): date-range filtering,
// time-based trends (no report exposes timestamps), profit/margin,
// utilization and collection-rate metrics, and any charting dependency
// (package.json has none — none is added).
const inr = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})

// Missing/invalid numbers render as a dash instead of "₹NaN".
function formatInr(value) {
  const n = Number(value)
  if (!Number.isFinite(n)) return '—'
  try {
    return inr.format(n)
  } catch {
    return `₹${n.toFixed(2)}`
  }
}

function formatCount(value) {
  const n = Number(value)
  return Number.isFinite(n) ? n.toLocaleString('en-IN') : '—'
}

// Share of a total, computed from backend counts only. Null when the total
// is missing/zero so we never render a fabricated "0.0%" or "NaN%".
function share(count, total) {
  const c = Number(count)
  const t = Number(total)
  if (!Number.isFinite(c) || !Number.isFinite(t) || t <= 0) return null
  return `${((c / t) * 100).toFixed(1)}%`
}

function CountRow({ label, count, total, note }) {
  const pct = share(count, total)
  return (
    <div>
      <dt>{label}</dt>
      <dd>
        {formatCount(count)}
        {pct && <span className="muted"> ({pct})</span>}
        {note && <span className="muted"> — {note}</span>}
      </dd>
    </div>
  )
}

// One independent load per report: a failure in one section never blocks the
// others, each keeps its own stale-response guard and retry.
function useReport(path) {
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [backendDown, setBackendDown] = useState(false)
  const seqRef = useRef(0)

  const load = () => {
    const seq = ++seqRef.current
    setLoading(true)
    setError('')
    setBackendDown(false)
    api.get(path)
      .then((res) => {
        if (seq !== seqRef.current) return // stale response
        setData(res)
        setLoading(false)
      })
      .catch((err) => {
        if (seq !== seqRef.current) return
        setData(null)
        setBackendDown(err?.status === 0)
        setError(err?.message || 'Failed to load this report.')
        setLoading(false)
      })
  }

  useEffect(() => { load() }, []) // eslint-disable-line react-hooks/exhaustive-deps

  return { data, loading, error, backendDown, load }
}

// Shared loading / error / offline / retry states for one report section.
function SectionStates({ report, what }) {
  if (report.loading) return <p className="state-note">Loading {what}…</p>
  if (!report.error) return null
  return (
    <div>
      <ErrorAlert message={report.error} />
      {report.backendDown && (
        <p className="muted">The backend server appears to be offline. Please try again shortly.</p>
      )}
      <button type="button" onClick={report.load}>Try again</button>
    </div>
  )
}

export default function ReportsPage() {
  const rentals = useReport('/api/admin/reports/rentals')
  const revenue = useReport('/api/admin/reports/revenue')
  const vehicles = useReport('/api/admin/reports/vehicles')
  const maintenance = useReport('/api/admin/reports/maintenance')

  return (
    <section>
      <h1>Reports</h1>

      <p className="muted small-note">
        Read-only aggregations over the entire database. The report API supports no date range or
        filters, so no date filtering is applied. Headline totals (customers, vehicles, rentals,
        payments, revenue, maintenance cost) are on the Admin Dashboard. Each section below loads
        independently.
      </p>

      {/* ---- rentals ---- */}
      <div className="card">
        <h2>Rental bookings by status</h2>
        <SectionStates report={rentals} what="the rentals report" />
        {!rentals.loading && !rentals.error && rentals.data && (
          <>
            <p className="rate">{formatCount(rentals.data.totalRentals)} total bookings</p>
            <dl className="confirmation-details">
              <CountRow
                label="Pending"
                count={rentals.data.pendingCount}
                total={rentals.data.totalRentals}
                note="created, not yet paid"
              />
              <CountRow
                label="Confirmed"
                count={rentals.data.confirmedCount}
                total={rentals.data.totalRentals}
                note="payment confirmed"
              />
              <CountRow
                label="Active"
                count={rentals.data.activeCount}
                total={rentals.data.totalRentals}
                note="ongoing rental"
              />
              <CountRow
                label="Completed"
                count={rentals.data.completedCount}
                total={rentals.data.totalRentals}
                note="vehicle returned"
              />
              <CountRow
                label="Cancelled"
                count={rentals.data.cancelledCount}
                total={rentals.data.totalRentals}
              />
            </dl>
            <p className="muted small-note">
              Counts are stored booking statuses. Bookings are not revenue — only confirmed payments
              count as income (see below).
            </p>
            {Number(rentals.data.totalRentals) === 0 && (
              <p className="state-note">No rental records exist yet.</p>
            )}
          </>
        )}
      </div>

      {/* ---- payments / revenue ---- */}
      <div className="card">
        <h2>Payment transactions</h2>
        <SectionStates report={revenue} what="the payments report" />
        {!revenue.loading && !revenue.error && revenue.data && (
          <>
            <p className="rate">
              {formatInr(revenue.data.totalRevenue)}
              <span> from successful payments</span>
            </p>
            <p className="muted small-note">
              Revenue is the summed amount of <strong>SUCCESS</strong> payments only. Booking totals,
              pending/failed/cancelled attempts and refunded records are excluded. REFUNDED appears
              as a stored status only — no refund capability exists in this system.
            </p>
            <dl className="confirmation-details">
              <CountRow
                label="Successful"
                count={revenue.data.successCount}
                total={revenue.data.totalTransactions}
              />
              <CountRow
                label="Pending"
                count={revenue.data.pendingCount}
                total={revenue.data.totalTransactions}
                note="attempt not confirmed"
              />
              <CountRow
                label="Failed"
                count={revenue.data.failedCount}
                total={revenue.data.totalTransactions}
              />
              <CountRow
                label="Cancelled"
                count={revenue.data.cancelledCount}
                total={revenue.data.totalTransactions}
              />
              <CountRow
                label="Refunded"
                count={revenue.data.refundedCount}
                total={revenue.data.totalTransactions}
              />
              <div>
                <dt>Total transactions</dt>
                <dd>{formatCount(revenue.data.totalTransactions)}</dd>
              </div>
            </dl>
            {Number(revenue.data.totalTransactions) === 0 && (
              <p className="state-note">No payment records exist yet.</p>
            )}
          </>
        )}
      </div>

      {/* ---- fleet ---- */}
      <div className="card">
        <h2>Fleet status</h2>
        <SectionStates report={vehicles} what="the vehicles report" />
        {!vehicles.loading && !vehicles.error && vehicles.data && (
          <>
            <p className="rate">{formatCount(vehicles.data.totalVehicles)} vehicles</p>
            <dl className="confirmation-details">
              <CountRow
                label="Available"
                count={vehicles.data.availableCount}
                total={vehicles.data.totalVehicles}
              />
              <CountRow
                label="Rented"
                count={vehicles.data.rentedCount}
                total={vehicles.data.totalVehicles}
              />
              <CountRow
                label="Maintenance"
                count={vehicles.data.maintenanceCount}
                total={vehicles.data.totalVehicles}
              />
              <CountRow
                label="Inactive"
                count={vehicles.data.inactiveCount}
                total={vehicles.data.totalVehicles}
              />
            </dl>
            <p className="muted small-note">
              Current stored status snapshot of every vehicle in the database.
            </p>
            {Number(vehicles.data.totalVehicles) === 0 && (
              <p className="state-note">No vehicles exist yet.</p>
            )}
          </>
        )}
      </div>

      {/* ---- popular vehicles ---- */}
      <div className="card">
        <h2>Popular vehicles</h2>
        <SectionStates report={vehicles} what="the popular-vehicles report" />
        {!vehicles.loading && !vehicles.error && vehicles.data && (
          <>
            <p className="muted small-note">
              Number of non-cancelled bookings (PENDING, CONFIRMED, ACTIVE, COMPLETED) per vehicle —
              a booking-popularity measure, not utilisation or income. Vehicles with no such booking
              are not listed.
            </p>
            {!Array.isArray(vehicles.data.popularVehicles) || vehicles.data.popularVehicles.length === 0 ? (
              <p className="state-note">No vehicle has any non-cancelled bookings yet.</p>
            ) : (
              <>
                {vehicles.data.popularVehicles.map((v) => (
                  <div key={v.vehicleId ?? `${v.brand}-${v.model}`}>
                    <p>
                      <strong>
                        {v.brand} {v.model} ({v.vehicleType ?? '—'})
                      </strong>{' '}
                      <span className="badge">
                        {formatCount(v.rentalCount)} booking{Number(v.rentalCount) === 1 ? '' : 's'}
                      </span>
                    </p>
                    <p className="muted small-note">Vehicle #{v.vehicleId ?? '—'}</p>
                  </div>
                ))}
              </>
            )}
          </>
        )}
      </div>

      {/* ---- maintenance ---- */}
      <div className="card">
        <h2>Maintenance costs</h2>
        <SectionStates report={maintenance} what="the maintenance report" />
        {!maintenance.loading && !maintenance.error && maintenance.data && (
          <>
            <p className="rate">{formatInr(maintenance.data.totalCost)} total cost</p>
            <dl className="confirmation-details">
              <div>
                <dt>Total records</dt>
                <dd>{formatCount(maintenance.data.totalRecords)}</dd>
              </div>
              <CountRow
                label="Scheduled"
                count={maintenance.data.scheduledCount}
                total={maintenance.data.totalRecords}
              />
              <CountRow
                label="In progress"
                count={maintenance.data.inProgressCount}
                total={maintenance.data.totalRecords}
              />
              <CountRow
                label="Completed"
                count={maintenance.data.completedCount}
                total={maintenance.data.totalRecords}
              />
              <CountRow
                label="Cancelled"
                count={maintenance.data.cancelledCount}
                total={maintenance.data.totalRecords}
              />
              <div>
                <dt>Total cost</dt>
                <dd>{formatInr(maintenance.data.totalCost)}</dd>
              </div>
              <div className="total-row">
                <dt>Average cost per record</dt>
                <dd>{formatInr(maintenance.data.averageCost)}</dd>
              </div>
            </dl>
            <p className="muted small-note">
              Cost totals include every record regardless of status (cancelled included); average is
              total cost ÷ total records, as computed by the backend.
            </p>
            {Number(maintenance.data.totalRecords) === 0 && (
              <p className="state-note">No maintenance records exist yet.</p>
            )}
          </>
        )}
      </div>

      <p className="muted small-note">
        Date-range reports, time-based trends, and profit, utilisation or collection-rate metrics
        are not available from the current backend and are therefore not shown.
      </p>
    </section>
  )
}
