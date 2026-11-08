import { useEffect, useRef, useState } from 'react'
import { api } from '../../services/api.js'
import ErrorAlert from '../../components/ErrorAlert.jsx'

// Phase 10B Step 6 admin customer management.
// Exact backend contracts (verified in AdminCustomerController/CustomerService):
//   GET   /api/admin/customers        -> 200 List<CustomerResponse> (CUSTOMER accounts only,
//                                        createdAt DESC; no search/filter/pagination params)
//   GET   /api/admin/customers/{id}   -> 200 CustomerResponse (404 for unknown or non-CUSTOMER)
//   PATCH /api/admin/customers/{id}/status -> 200 CustomerResponse
//         body { status } — only ACTIVE/INACTIVE (UserStatus), case-insensitive;
//         400 blank/invalid value, 404 unknown or non-CUSTOMER account.
// CustomerResponse: userId, fullName, email, phone, address, city, state,
// pincode, role, status, createdAt, updatedAt — no credential fields exist.
// Unsupported by the backend (intentionally not implemented): editing customer
// information, deleting customers, server-side search/filter/pagination, and
// a per-customer rentals endpoint. Rental history below lazily loads
// GET /api/admin/rentals (verified admin endpoint, RentalResponse.userId is
// populated) and filters it client-side.
const fmt = (value) => (value ? String(value).replace('T', ' ') : '—')
const STATUS_OPTIONS = ['ACTIVE', 'INACTIVE']

export default function CustomersPage() {
  const [customers, setCustomers] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [backendDown, setBackendDown] = useState(false)

  // Client-side filtering over the complete loaded list (the API offers no
  // search/filter parameters — clearly marked as in-browser below).
  const [search, setSearch] = useState('')
  const [statusFilter, setStatusFilter] = useState('')

  const [selectedId, setSelectedId] = useState(null)
  const [detail, setDetail] = useState(null)
  const [detailLoading, setDetailLoading] = useState(false)
  const [detailError, setDetailError] = useState('')
  const [detailDown, setDetailDown] = useState(false)
  const detailSeqRef = useRef(0) // ignore out-of-order detail responses
  const listSeqRef = useRef(0) // ignore out-of-order list responses

  const [statusChoice, setStatusChoice] = useState('')
  const [statusUpdating, setStatusUpdating] = useState(false)
  const [statusError, setStatusError] = useState('')
  const [statusOk, setStatusOk] = useState('')

  // Lazy, client-side-filtered rental history from the admin rentals list.
  const [rentalsAll, setRentalsAll] = useState(null) // null = not loaded yet
  const [rentalsLoading, setRentalsLoading] = useState(false)
  const [rentalsError, setRentalsError] = useState('')
  const rentalsSeqRef = useRef(0) // ignore out-of-order rental responses

  const load = () => {
    const seq = ++listSeqRef.current
    setLoading(true)
    setError('')
    setBackendDown(false)
    api.get('/api/admin/customers')
      .then((data) => {
        if (seq !== listSeqRef.current) return // stale response
        setCustomers(Array.isArray(data) ? data : [])
        setLoading(false)
      })
      .catch((err) => {
        if (seq !== listSeqRef.current) return
        setCustomers([])
        setBackendDown(err?.status === 0)
        setError(err?.message || 'Failed to load customers.')
        setLoading(false)
      })
  }

  useEffect(() => { load() }, [])

  const openDetail = (customerId) => {
    const seq = ++detailSeqRef.current
    setSelectedId(customerId)
    setDetail(null)
    setDetailError('')
    setDetailDown(false)
    setDetailLoading(true)
    setStatusChoice('')
    setStatusError('')
    setStatusOk('')
    api.get(`/api/admin/customers/${customerId}`)
      .then((data) => {
        if (seq === detailSeqRef.current) setDetail(data)
      })
      .catch((err) => {
        if (seq !== detailSeqRef.current) return
        setDetailDown(err?.status === 0)
        setDetailError(err?.message || 'Failed to load the customer.')
      })
      .finally(() => {
        if (seq === detailSeqRef.current) setDetailLoading(false)
      })
  }

  const closeDetail = () => {
    detailSeqRef.current += 1 // invalidate any in-flight detail request
    setSelectedId(null)
    setDetail(null)
    setDetailError('')
    setDetailDown(false)
    setStatusChoice('')
    setStatusError('')
    setStatusOk('')
  }

  const setStatusField = (e) => {
    setStatusChoice(e.target.value)
    setStatusError('')
  }

  const onSubmitStatus = async (e) => {
    e.preventDefault()
    if (statusUpdating || !detail) return // duplicate-submission guard (button also disabled)
    const target = (statusChoice || '').trim().toUpperCase()
    if (!STATUS_OPTIONS.includes(target)) {
      setStatusError('Select a status to apply.')
      return
    }
    if (target === detail.status) return // never send a no-op change
    if (target === 'INACTIVE') {
      const confirmed = window.confirm(
        `Set ${detail.fullName || `customer #${detail.userId}`} to INACTIVE? You can reactivate them at any time.`,
      )
      if (!confirmed) return
    }
    setStatusUpdating(true)
    setStatusError('')
    setStatusOk('')
    try {
      const changed = await api.patch(`/api/admin/customers/${selectedId}/status`, { status: target })
      setStatusChoice('')
      setStatusOk(
        `Status of ${changed?.fullName || `customer #${selectedId}`} changed to ${changed?.status || target}.`,
      )
      // Refresh list and detail with the existing fetch logic. A refresh
      // failure surfaces in the list/detail error states whose retry only
      // re-runs GETs — the success message stays visible, so the status
      // change is never reported as failed and resubmission is not
      // encouraged.
      load()
      openDetail(selectedId)
    } catch (err) {
      // Keep the current choice so the admin can correct and retry.
      setStatusError(err?.message || 'Failed to update the status.')
    } finally {
      setStatusUpdating(false)
    }
  }

  const loadRentals = () => {
    if (rentalsLoading) return // duplicate-request guard (button also disabled)
    const seq = ++rentalsSeqRef.current
    setRentalsLoading(true)
    setRentalsError('')
    api.get('/api/admin/rentals')
      .then((data) => {
        if (seq !== rentalsSeqRef.current) return // stale response
        setRentalsAll(Array.isArray(data) ? data : [])
      })
      .catch((err) => {
        if (seq !== rentalsSeqRef.current) return
        setRentalsError(err?.message || 'Failed to load rental history.')
      })
      .finally(() => {
        if (seq === rentalsSeqRef.current) setRentalsLoading(false)
      })
  }

  // ---- detail view ----
  if (selectedId !== null) {
    const myRentals = Array.isArray(rentalsAll) && detail
      ? rentalsAll.filter((r) => r?.userId === detail.userId)
      : []
    return (
      <section>
        <p>
          <button type="button" className="button-secondary" onClick={closeDetail} disabled={statusUpdating}>
            ← Back to customers
          </button>
        </p>

        {statusOk && <p className="success-alert" role="status">{statusOk}</p>}

        {detailLoading && <p className="state-note">Loading customer…</p>}

        {!detailLoading && detailError && (
          <div>
            <ErrorAlert message={detailError} />
            {detailDown && (
              <p className="muted">The backend server appears to be offline. Please try again shortly.</p>
            )}
            <button type="button" onClick={() => openDetail(selectedId)}>Try again</button>
          </div>
        )}

        {!detailLoading && !detailError && detail && (
          <>
            <div className="card">
              <h1>{detail.fullName || `Customer #${detail.userId}`}</h1>
              <p className="badges">
                <span className="badge">{detail.role}</span>
                <span className="badge">{detail.status}</span>
              </p>
              <dl className="confirmation-details">
                <div><dt>User ID</dt><dd>{detail.userId}</dd></div>
                <div><dt>Email</dt><dd>{detail.email ?? '—'}</dd></div>
                <div><dt>Phone</dt><dd>{detail.phone ?? '—'}</dd></div>
                <div><dt>Address</dt><dd>{detail.address ?? '—'}</dd></div>
                <div><dt>City</dt><dd>{detail.city ?? '—'}</dd></div>
                <div><dt>State</dt><dd>{detail.state ?? '—'}</dd></div>
                <div><dt>Pincode</dt><dd>{detail.pincode ?? '—'}</dd></div>
                <div><dt>Status</dt><dd>{detail.status}</dd></div>
                <div><dt>Joined</dt><dd>{fmt(detail.createdAt)}</dd></div>
                <div><dt>Last updated</dt><dd>{fmt(detail.updatedAt)}</dd></div>
              </dl>
            </div>

            <div className="card">
              <h2>Change status</h2>
              <p>
                Current status: <span className="badge">{detail.status}</span>
              </p>
              <form onSubmit={onSubmitStatus} noValidate>
                <div className="control">
                  <label htmlFor="cust-status">Set status to</label>
                  <select id="cust-status" value={statusChoice} onChange={setStatusField}>
                    <option value="">Select status</option>
                    {STATUS_OPTIONS.filter((s) => s !== detail.status).map((s) => (
                      <option key={s} value={s}>{s}</option>
                    ))}
                  </select>
                </div>
                <ErrorAlert message={statusError} />
                <button type="submit" className="button-success" disabled={statusUpdating || !statusChoice}>
                  {statusUpdating ? 'Updating…' : 'Change status'}
                </button>
              </form>
            </div>

            <div className="card">
              <h2>Rental history</h2>
              <p className="muted small-note">
                Filtered in your browser from the full admin rentals list (no per-customer
                endpoint exists on the backend).
              </p>
              <ErrorAlert message={rentalsError} />
              <button type="button" onClick={loadRentals} disabled={rentalsLoading}>
                {rentalsLoading
                  ? 'Loading rentals…'
                  : rentalsAll == null
                    ? 'Load rental history'
                    : 'Refresh rental history'}
              </button>
              {Array.isArray(rentalsAll) && !rentalsLoading && !rentalsError && (
                <>
                  <p className="muted">
                    {myRentals.length} rental{myRentals.length === 1 ? '' : 's'} for this customer
                  </p>
                  {myRentals.length === 0 && (
                    <p className="state-note">No rentals found for this customer.</p>
                  )}
                  {myRentals.map((r) => (
                    <div key={r.rentalId}>
                      <p>
                        <strong>Rental #{r.rentalId}</strong>{' '}
                        <span className={`badge badge-${(r.rentalStatus || '').toLowerCase()}`}>
                          {r.rentalStatus}
                        </span>
                      </p>
                      <p className="muted small-note">
                        {r.vehicleBrand} {r.vehicleModel} ({r.vehicleRegistrationNumber ?? '—'}) ·{' '}
                        {fmt(r.pickupDateTime)} → {fmt(r.returnDateTime)} · ₹{r.totalAmount ?? '—'}
                      </p>
                    </div>
                  ))}
                </>
              )}
            </div>
          </>
        )}
      </section>
    )
  }

  // ---- list view ----
  const query = search.trim().toLowerCase()
  const filtered = customers.filter((c) => {
    if (statusFilter && c.status !== statusFilter) return false
    if (!query) return true
    return [c.fullName, c.email, c.phone, c.city, c.state, c.pincode]
      .some((v) => (v ?? '').toLowerCase().includes(query))
  })

  return (
    <section>
      <h1>Customers</h1>

      <div className="controls">
        <div className="control">
          <label htmlFor="cust-search">Search (name, email, phone, city)</label>
          <input
            id="cust-search"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Type to filter…"
          />
        </div>
        <div className="control">
          <label htmlFor="cust-filter">Status</label>
          <select id="cust-filter" value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
            <option value="">All statuses</option>
            {STATUS_OPTIONS.map((s) => (
              <option key={s} value={s}>{s}</option>
            ))}
          </select>
        </div>
      </div>
      <p className="muted small-note">
        Searching and filtering run in your browser over the complete list loaded from the server.
      </p>

      {loading && <p className="state-note">Loading customers…</p>}

      {!loading && (
        <>
          <ErrorAlert message={error} />
          {backendDown && error && (
            <p className="muted">The backend server appears to be offline. Please try again shortly.</p>
          )}
          {error && <button type="button" onClick={load}>Try again</button>}
        </>
      )}

      {!loading && !error && customers.length === 0 && (
        <div className="state-note">
          <p>No customers found.</p>
        </div>
      )}

      {!loading && !error && customers.length > 0 && filtered.length === 0 && (
        <div className="state-note">
          <p>No customers match the current search or filter.</p>
        </div>
      )}

      {!loading && !error && filtered.length > 0 && (
        <>
          <p className="muted">
            {filtered.length} of {customers.length} customer{customers.length === 1 ? '' : 's'}
          </p>
          {filtered.map((c) => (
            <article key={c.userId} className="card">
              <h3>{c.fullName || `Customer #${c.userId}`}</h3>
              <p className="badges">
                <span className="badge">{c.status}</span>
                {c.city && <span className="badge">{c.city}</span>}
              </p>
              <dl className="vehicle-facts">
                <div><dt>Email</dt><dd>{c.email ?? '—'}</dd></div>
                <div><dt>Phone</dt><dd>{c.phone ?? '—'}</dd></div>
                <div><dt>Joined</dt><dd>{fmt(c.createdAt)}</dd></div>
              </dl>
              <button type="button" onClick={() => openDetail(c.userId)}>View details</button>
            </article>
          ))}
        </>
      )}
    </section>
  )
}
