import { useEffect, useState } from 'react'
import { api } from '../../services/api.js'
import ErrorAlert from '../../components/ErrorAlert.jsx'

// Phase 10A admin dashboard overview.
// Exact backend contract: GET /api/admin/reports/summary -> SummaryReportResponse
// { totalCustomers, totalVehicles, totalRentals, totalPayments (long),
//   totalRevenue, totalMaintenanceCost (BigDecimal) }.
// ADMIN-protected by the existing /api/admin/** security rule.
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

export default function AdminDashboard() {
  const [summary, setSummary] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [backendDown, setBackendDown] = useState(false)

  const load = () => {
    setLoading(true)
    setError('')
    setBackendDown(false)
    api.get('/api/admin/reports/summary')
      .then((data) => setSummary(data))
      .catch((err) => {
        setSummary(null)
        setBackendDown(err?.status === 0)
        setError(err?.message || 'Failed to load the dashboard summary.')
      })
      .finally(() => setLoading(false))
  }

  useEffect(() => { load() }, [])

  return (
    <section>
      <h1>Admin Dashboard</h1>

      {loading && <p className="state-note">Loading summary…</p>}

      {!loading && (
        <>
          <ErrorAlert message={error} />
          {backendDown && error && (
            <p className="muted">The backend server appears to be offline. Please try again shortly.</p>
          )}
          {error && (
            <button type="button" onClick={load}>Try again</button>
          )}
        </>
      )}

      {!loading && !error && summary && (
        <>
          <div className="card">
            <h2>Fleet &amp; business summary</h2>
            <dl className="confirmation-details">
              <div><dt>Total customers</dt><dd>{formatCount(summary.totalCustomers)}</dd></div>
              <div><dt>Total vehicles</dt><dd>{formatCount(summary.totalVehicles)}</dd></div>
              <div><dt>Total rentals</dt><dd>{formatCount(summary.totalRentals)}</dd></div>
              <div><dt>Total payments</dt><dd>{formatCount(summary.totalPayments)}</dd></div>
              <div><dt>Total maintenance cost</dt><dd>{formatInr(summary.totalMaintenanceCost)}</dd></div>
              <div className="total-row"><dt>Total revenue</dt><dd>{formatInr(summary.totalRevenue)}</dd></div>
            </dl>
          </div>

          <p className="muted small-note">
            Figures are read-only aggregates over the current database state.
          </p>
        </>
      )}
    </section>
  )
}
