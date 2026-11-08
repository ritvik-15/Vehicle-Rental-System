import { useEffect, useRef, useState } from 'react'
import { api } from '../../services/api.js'
import ErrorAlert from '../../components/ErrorAlert.jsx'

// Phase 10 Step 8 admin payment management — READ-ONLY by verified contract.
// Backend (AdminPaymentController is deliberately GET-only: "refunds, status
// changes and any gateway interaction are explicitly out of scope"):
//   GET /api/admin/payments -> 200 List<PaymentResponse>
//      (every payment, createdAt DESC; no params, no pagination, no filters,
//       no detail endpoint, no mutation endpoints).
// PaymentResponse: paymentId, rentalId, userId, amount, paymentMethod,
// razorpayOrderId, razorpayPaymentId, paymentStatus, paymentDate, createdAt.
// PaymentStatus: PENDING, SUCCESS, FAILED, CANCELLED, REFUNDED.
// PaymentMethod: RAZORPAY (only value). All values are DEMO records persisted
// in MySQL — no gateway integration, refunds, edits, or deletions exist here,
// and none are invented on this page.
const fmt = (value) => (value ? String(value).replace('T', ' ') : '—')
const STATUSES = ['PENDING', 'SUCCESS', 'FAILED', 'CANCELLED', 'REFUNDED']

export default function PaymentsPage() {
  const [payments, setPayments] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [backendDown, setBackendDown] = useState(false)
  const listSeqRef = useRef(0) // ignore out-of-order list responses

  // Client-side filtering over the complete loaded list (the API offers no
  // search/filter parameters — clearly marked as in-browser below).
  const [search, setSearch] = useState('')
  const [statusFilter, setStatusFilter] = useState('')

  const load = () => {
    const seq = ++listSeqRef.current
    setLoading(true)
    setError('')
    setBackendDown(false)
    api.get('/api/admin/payments')
      .then((data) => {
        if (seq !== listSeqRef.current) return // stale response
        setPayments(Array.isArray(data) ? data : [])
        setLoading(false)
      })
      .catch((err) => {
        if (seq !== listSeqRef.current) return
        setPayments([])
        setBackendDown(err?.status === 0)
        setError(err?.message || 'Failed to load payments.')
        setLoading(false)
      })
  }

  useEffect(() => { load() }, [])

  const query = search.trim().toLowerCase()
  const filtered = payments.filter((p) => {
    if (statusFilter && p.paymentStatus !== statusFilter) return false
    if (!query) return true
    return [
      String(p.paymentId ?? ''),
      String(p.rentalId ?? ''),
      String(p.userId ?? ''),
      p.razorpayOrderId,
      p.razorpayPaymentId,
      p.paymentMethod,
    ].some((v) => (v ?? '').toLowerCase().includes(query))
  })

  return (
    <section>
      <h1>Payments</h1>

      <p className="muted small-note">
        Read-only view of the demo payment records stored in the database. The backend supports
        listing only — no refunds, edits, status changes, or payment-gateway actions exist here.
      </p>

      <div className="controls">
        <div className="control">
          <label htmlFor="pay-search">Search (payment, rental, user, order ID)</label>
          <input
            id="pay-search"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Type to filter…"
          />
        </div>
        <div className="control">
          <label htmlFor="pay-filter">Status</label>
          <select id="pay-filter" value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
            <option value="">All statuses</option>
            {STATUSES.map((s) => (
              <option key={s} value={s}>{s}</option>
            ))}
          </select>
        </div>
      </div>
      <p className="muted small-note">
        Searching and filtering run in your browser over the complete list loaded from the server.
      </p>

      {loading && <p className="state-note">Loading payments…</p>}

      {!loading && (
        <>
          <ErrorAlert message={error} />
          {backendDown && error && (
            <p className="muted">The backend server appears to be offline. Please try again shortly.</p>
          )}
          {error && <button type="button" onClick={load}>Try again</button>}
        </>
      )}

      {!loading && !error && payments.length === 0 && (
        <div className="state-note">
          <p>No payment records found.</p>
        </div>
      )}

      {!loading && !error && payments.length > 0 && filtered.length === 0 && (
        <div className="state-note">
          <p>No payments match the current search or filter.</p>
        </div>
      )}

      {!loading && !error && filtered.length > 0 && (
        <>
          <p className="muted">
            {filtered.length} of {payments.length} payment{payments.length === 1 ? '' : 's'}
          </p>
          {filtered.map((p) => (
            <article key={p.paymentId ?? `${p.rentalId}-${p.createdAt}`} className="card">
              <h3>
                Payment #{p.paymentId ?? '—'}{' '}
                <span className={`badge badge-${(p.paymentStatus || '').toLowerCase()}`}>
                  {p.paymentStatus ?? '—'}
                </span>
              </h3>
              <p className="rate">₹{p.amount ?? '—'}</p>
              <dl className="vehicle-facts">
                <div><dt>Rental</dt><dd>Rental #{p.rentalId ?? '—'}</dd></div>
                <div><dt>Customer</dt><dd>User #{p.userId ?? '—'}</dd></div>
                <div><dt>Method</dt><dd>{p.paymentMethod ?? '—'}</dd></div>
                <div><dt>Paid on</dt><dd>{fmt(p.paymentDate)}</dd></div>
                <div><dt>Recorded</dt><dd>{fmt(p.createdAt)}</dd></div>
              </dl>
              <p className="muted small-note">
                Order ID: {p.razorpayOrderId ?? '—'} · Payment ID: {p.razorpayPaymentId ?? '—'}
                <br />
                <span className="muted">
                  (Demo identifiers persisted with the record — no live gateway is involved.)
                </span>
              </p>
            </article>
          ))}
        </>
      )}
    </section>
  )
}
