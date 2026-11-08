import { useEffect, useRef, useState } from 'react'
import { api } from '../../services/api.js'
import ErrorAlert from '../../components/ErrorAlert.jsx'

// Phase 10B Step 7 admin rental management.
// Exact backend contracts (verified in AdminRentalController/RentalService):
//   GET   /api/admin/rentals          -> 200 List<RentalResponse> (createdAt DESC; no search/filter params)
//   GET   /api/admin/rentals/{id}     -> 200 RentalResponse (admin-permissive; 404 unknown)
//   PATCH /api/admin/rentals/{id}/status -> 200 RentalResponse; body { status } (case-insensitive)
//   PATCH /api/admin/rentals/{id}/return -> 200 RentalResponse (no body; ACTIVE -> COMPLETED only)
// Admin transition matrix (RentalService.updateRentalStatusForAdmin):
//   PENDING  -> CANCELLED
//   CONFIRMED -> CANCELLED | ACTIVE (only once server time has reached pickup)
//   ACTIVE   -> (return endpoint only) COMPLETED, recording actual return time
//   COMPLETED / CANCELLED are terminal; -> CONFIRMED is rejected: rentals are
//   confirmed exclusively through successful payment (Phase 8 flow), never here.
// Cancellation (cancelRentalCore) changes no payment records and performs no
// refund; it only releases a reserved promo usage for unpaid PENDING bookings.
// RentalResponse carries userId + vehicle summary (no customer name/email and
// no payment fields exist), plus full pricing and timestamps.
const fmt = (value) => (value ? String(value).replace('T', ' ') : '—')
const STATUSES = ['PENDING', 'CONFIRMED', 'ACTIVE', 'COMPLETED', 'CANCELLED']

export default function RentalsPage() {
  const [rentals, setRentals] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [backendDown, setBackendDown] = useState(false)
  const listSeqRef = useRef(0) // ignore out-of-order list responses

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

  const [actionUpdating, setActionUpdating] = useState(false)
  const [actionError, setActionError] = useState('')
  const [actionOk, setActionOk] = useState('')

  const load = () => {
    const seq = ++listSeqRef.current
    setLoading(true)
    setError('')
    setBackendDown(false)
    api.get('/api/admin/rentals')
      .then((data) => {
        if (seq !== listSeqRef.current) return // stale response
        setRentals(Array.isArray(data) ? data : [])
        setLoading(false)
      })
      .catch((err) => {
        if (seq !== listSeqRef.current) return
        setRentals([])
        setBackendDown(err?.status === 0)
        setError(err?.message || 'Failed to load rentals.')
        setLoading(false)
      })
  }

  useEffect(() => { load() }, [])

  const openDetail = (rentalId) => {
    const seq = ++detailSeqRef.current
    setSelectedId(rentalId)
    setDetail(null)
    setDetailError('')
    setDetailDown(false)
    setDetailLoading(true)
    setActionError('')
    setActionOk('')
    api.get(`/api/admin/rentals/${rentalId}`)
      .then((data) => {
        if (seq === detailSeqRef.current) setDetail(data)
      })
      .catch((err) => {
        if (seq !== detailSeqRef.current) return
        setDetailDown(err?.status === 0)
        setDetailError(err?.message || 'Failed to load the rental.')
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
    setActionError('')
    setActionOk('')
  }

  // One verified transition per call. The server matrix is authoritative —
  // its validation/business-rule messages are surfaced verbatim, and success
  // is reported only from the server response, never from local state.
  const runAction = async ({ confirmMessage, request, okLabel }) => {
    if (actionUpdating || !detail) return // duplicate-submission guard (buttons also disabled)
    if (!window.confirm(confirmMessage)) return
    setActionUpdating(true)
    setActionError('')
    setActionOk('')
    try {
      const updated = await request()
      setActionOk(
        `${okLabel} — rental #${updated?.rentalId ?? selectedId} is now ${updated?.rentalStatus ?? 'updated'}.`,
      )
      // Refresh list and detail with the existing fetch logic. A refresh
      // failure surfaces in the list/detail error states whose retry only
      // re-runs GETs — the success message stays visible, so the action is
      // never reported as failed and resubmission is not encouraged.
      load()
      openDetail(selectedId)
    } catch (err) {
      setActionError(err?.message || 'The action failed.')
    } finally {
      setActionUpdating(false)
    }
  }

  const onCancelRental = () => runAction({
    confirmMessage:
      `Cancel rental #${detail.rentalId} (${detail.vehicleBrand} ${detail.vehicleModel})? ` +
      'This cannot be undone. No payment refund is performed by this action.',
    request: () => api.patch(`/api/admin/rentals/${selectedId}/status`, { status: 'CANCELLED' }),
    okLabel: 'Rental cancelled',
  })

  const onActivateRental = () => runAction({
    confirmMessage:
      `Activate rental #${detail.rentalId}? This marks the vehicle as picked up (status ACTIVE).`,
    request: () => api.patch(`/api/admin/rentals/${selectedId}/status`, { status: 'ACTIVE' }),
    okLabel: 'Rental activated',
  })

  const onReturnRental = () => runAction({
    confirmMessage:
      `Record the return of rental #${detail.rentalId} and complete it? The server records the ` +
      'current time as the actual return time; pricing is not recalculated.',
    request: () => api.patch(`/api/admin/rentals/${selectedId}/return`),
    okLabel: 'Rental returned',
  })

  // ---- detail view ----
  if (selectedId !== null) {
    // UX guard that mirrors the server's own activation rule (pickup time
    // reached); the backend re-checks and stays authoritative.
    const pickupReached = detail?.pickupDateTime
      ? new Date(detail.pickupDateTime).getTime() <= Date.now()
      : true
    return (
      <section>
        <p>
          <button type="button" className="button-secondary" onClick={closeDetail} disabled={actionUpdating}>
            ← Back to rentals
          </button>
        </p>

        {actionOk && <p className="success-alert" role="status">{actionOk}</p>}

        {detailLoading && <p className="state-note">Loading rental…</p>}

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
              <h1>Rental #{detail.rentalId}</h1>
              <p className="badges">
                <span className={`badge badge-${(detail.rentalStatus || '').toLowerCase()}`}>
                  {detail.rentalStatus}
                </span>
              </p>
              <p>
                {detail.vehicleBrand} {detail.vehicleModel}{' '}
                <span className="muted">({detail.vehicleRegistrationNumber ?? '—'})</span>
              </p>
              <dl className="confirmation-details">
                <div><dt>Customer</dt><dd>User #{detail.userId ?? '—'}</dd></div>
                <div><dt>Vehicle ID</dt><dd>{detail.vehicleId ?? '—'}</dd></div>
                <div><dt>Pickup</dt><dd>{fmt(detail.pickupDateTime)}</dd></div>
                <div><dt>Return (scheduled)</dt><dd>{fmt(detail.returnDateTime)}</dd></div>
                <div><dt>Actual return</dt><dd>{fmt(detail.actualReturnDatetime)}</dd></div>
                <div><dt>Duration</dt><dd>{detail.numberOfDays ?? '—'} day{detail.numberOfDays === 1 ? '' : 's'}</dd></div>
                <div><dt>Daily rate</dt><dd>₹{detail.dailyRate ?? '—'}</dd></div>
                <div><dt>Subtotal</dt><dd>₹{detail.subtotal ?? '—'}</dd></div>
                <div>
                  <dt>Discount{detail.promoCode ? ` (${detail.promoCode})` : ''}</dt>
                  <dd>{Number(detail.discountAmount) > 0 ? `-₹${detail.discountAmount}` : '₹0'}</dd>
                </div>
                <div><dt>Tax (18%)</dt><dd>₹{detail.taxAmount ?? '—'}</dd></div>
                <div><dt>Security deposit (20%)</dt><dd>₹{detail.securityDeposit ?? '—'}</dd></div>
                <div className="total-row"><dt>Total</dt><dd>₹{detail.totalAmount ?? '—'}</dd></div>
                <div><dt>Booked on</dt><dd>{fmt(detail.createdAt)}</dd></div>
                <div><dt>Last updated</dt><dd>{fmt(detail.updatedAt)}</dd></div>
              </dl>
              {detail.notes && <p className="muted small-note">Notes: {detail.notes}</p>}
            </div>

            <div className="card">
              <h2>Actions</h2>
              <ErrorAlert message={actionError} />

              {detail.rentalStatus === 'PENDING' && (
                <>
                  <p className="muted small-note">
                    Unpaid booking. Confirmation happens only through the customer payment flow —
                    it is not available here.
                  </p>
                  <button type="button" className="button-secondary" onClick={onCancelRental} disabled={actionUpdating}>
                    {actionUpdating ? 'Working…' : 'Cancel rental'}
                  </button>
                </>
              )}

              {detail.rentalStatus === 'CONFIRMED' && (
                <>
                  <p>
                    <button type="button" className="button-success" onClick={onActivateRental} disabled={actionUpdating || !pickupReached}>
                      {actionUpdating ? 'Working…' : 'Activate rental (mark as picked up)'}
                    </button>
                  </p>
                  {!pickupReached && (
                    <p className="muted small-note">
                      Activation becomes available once the pickup time is reached.
                    </p>
                  )}
                  <button type="button" className="button-secondary" onClick={onCancelRental} disabled={actionUpdating}>
                    {actionUpdating ? 'Working…' : 'Cancel rental'}
                  </button>
                </>
              )}

              {detail.rentalStatus === 'ACTIVE' && (
                <>
                  <p className="muted small-note">
                    The vehicle has been picked up. Recording the return completes the rental.
                  </p>
                  <button type="button" className="button-success" onClick={onReturnRental} disabled={actionUpdating}>
                    {actionUpdating ? 'Working…' : 'Record return (complete rental)'}
                  </button>
                </>
              )}

              {(detail.rentalStatus === 'COMPLETED' || detail.rentalStatus === 'CANCELLED') && (
                <p className="muted small-note">
                  This rental is {detail.rentalStatus.toLowerCase()} and no further actions are available.
                </p>
              )}
            </div>
          </>
        )}
      </section>
    )
  }

  // ---- list view ----
  const query = search.trim().toLowerCase()
  const filtered = rentals.filter((r) => {
    if (statusFilter && r.rentalStatus !== statusFilter) return false
    if (!query) return true
    return [
      String(r.rentalId ?? ''),
      String(r.userId ?? ''),
      r.vehicleBrand,
      r.vehicleModel,
      r.vehicleRegistrationNumber,
      r.promoCode,
      r.notes,
    ].some((v) => (v ?? '').toLowerCase().includes(query))
  })

  return (
    <section>
      <h1>Rentals</h1>

      <div className="controls">
        <div className="control">
          <label htmlFor="rental-search">Search (ID, customer ID, vehicle, promo)</label>
          <input
            id="rental-search"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Type to filter…"
          />
        </div>
        <div className="control">
          <label htmlFor="rental-filter">Status</label>
          <select id="rental-filter" value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
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

      {loading && <p className="state-note">Loading rentals…</p>}

      {!loading && (
        <>
          <ErrorAlert message={error} />
          {backendDown && error && (
            <p className="muted">The backend server appears to be offline. Please try again shortly.</p>
          )}
          {error && <button type="button" onClick={load}>Try again</button>}
        </>
      )}

      {!loading && !error && rentals.length === 0 && (
        <div className="state-note">
          <p>No rentals found.</p>
        </div>
      )}

      {!loading && !error && rentals.length > 0 && filtered.length === 0 && (
        <div className="state-note">
          <p>No rentals match the current search or filter.</p>
        </div>
      )}

      {!loading && !error && filtered.length > 0 && (
        <>
          <p className="muted">
            {filtered.length} of {rentals.length} rental{rentals.length === 1 ? '' : 's'}
          </p>
          {filtered.map((r) => (
            <article key={r.rentalId} className="card">
              <h3>
                Rental #{r.rentalId}{' '}
                <span className={`badge badge-${(r.rentalStatus || '').toLowerCase()}`}>
                  {r.rentalStatus}
                </span>
              </h3>
              <p>
                {r.vehicleBrand} {r.vehicleModel}{' '}
                <span className="muted">({r.vehicleRegistrationNumber ?? '—'})</span>
              </p>
              <dl className="vehicle-facts">
                <div><dt>Customer</dt><dd>User #{r.userId ?? '—'}</dd></div>
                <div><dt>Pickup</dt><dd>{fmt(r.pickupDateTime)}</dd></div>
                <div><dt>Return</dt><dd>{fmt(r.returnDateTime)}</dd></div>
                <div><dt>Days</dt><dd>{r.numberOfDays ?? '—'}</dd></div>
                <div><dt>Total</dt><dd>₹{r.totalAmount ?? '—'}</dd></div>
              </dl>
              <button type="button" onClick={() => openDetail(r.rentalId)}>View details</button>
            </article>
          ))}
        </>
      )}
    </section>
  )
}
