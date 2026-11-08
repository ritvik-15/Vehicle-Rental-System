import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../../services/api.js'
import ErrorAlert from '../../components/ErrorAlert.jsx'

// Phase 9 customer rentals.
// Exact backend contracts (no invented fields):
//   GET   /api/rentals/my        -> RentalResponse[]
//   PATCH /api/rentals/{id}/cancel -> RentalResponse (ownership enforced server-side)
// Cancellation eligibility mirrors RentalService.cancelRental: only PENDING or
// CONFIRMED rentals whose pickup time is still in the future; the backend
// remains authoritative and its rejection messages are surfaced verbatim.
function canCancel(rental) {
  if (rental.rentalStatus !== 'PENDING' && rental.rentalStatus !== 'CONFIRMED') return false
  const pickup = new Date(rental.pickupDateTime)
  return !Number.isNaN(pickup.getTime()) && pickup.getTime() > Date.now()
}

const fmt = (value) => (value ? String(value).replace('T', ' ') : '—')

export default function MyRentalsPage() {
  const [rentals, setRentals] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [backendDown, setBackendDown] = useState(false)
  const [notice, setNotice] = useState('')
  const [cancellingId, setCancellingId] = useState(null)
  const cancellingRef = useRef(false)
  const seqRef = useRef(0) // ignore out-of-order list responses

  const load = () => {
    const seq = ++seqRef.current
    setLoading(true)
    setError('')
    setBackendDown(false)
    api.get('/api/rentals/my')
      .then((data) => {
        if (seq !== seqRef.current) return
        setRentals(Array.isArray(data) ? data : [])
        setLoading(false)
      })
      .catch((err) => {
        if (seq !== seqRef.current) return
        setRentals([])
        setBackendDown(err?.status === 0)
        setError(err?.message || 'Failed to load your rentals.')
        setLoading(false)
      })
  }

  useEffect(() => { load() }, [])

  const onCancel = async (rental) => {
    if (cancellingRef.current) return
    const confirmed = window.confirm(
      `Cancel rental #${rental.rentalId} for ${rental.vehicleBrand} ${rental.vehicleModel} ` +
      `(pickup ${fmt(rental.pickupDateTime)})? This cannot be undone.`,
    )
    if (!confirmed) return
    cancellingRef.current = true
    setCancellingId(rental.rentalId)
    setError('')
    setNotice('')
    try {
      await api.patch(`/api/rentals/${rental.rentalId}/cancel`)
      setNotice(`Rental #${rental.rentalId} has been cancelled.`)
      load() // refresh from the backend
    } catch (err) {
      setBackendDown(err?.status === 0)
      setError(err?.message || 'Cancellation failed. Please try again.')
    } finally {
      cancellingRef.current = false
      setCancellingId(null)
    }
  }

  return (
    <section>
      <h1>My Rentals</h1>

      {notice && <p className="success-alert" role="status">{notice}</p>}
      <ErrorAlert message={error} />
      {backendDown && error && (
        <p className="muted">The backend server appears to be offline. Please try again shortly.</p>
      )}

      {loading && <p className="state-note">Loading rentals…</p>}

      {!loading && !error && rentals.length === 0 && (
        <div className="state-note">
          <p>You have no rentals yet.</p>
          <Link className="button" to="/home">Browse vehicles</Link>
        </div>
      )}

      {!loading && rentals.length > 0 && (
        <p className="muted">{rentals.length} rental{rentals.length === 1 ? '' : 's'}</p>
      )}

      {!loading && rentals.map((rental) => (
        <article key={rental.rentalId} className="card">
          <h2>
            Rental #{rental.rentalId}{' '}
            <span className={`badge badge-${(rental.rentalStatus || '').toLowerCase()}`}>
              {rental.rentalStatus}
            </span>
          </h2>
          <p>
            <Link to={`/home/vehicles/${rental.vehicleId}`}>
              {rental.vehicleBrand} {rental.vehicleModel}
            </Link>
            {' '}<span className="muted">({rental.vehicleRegistrationNumber})</span>
          </p>
          <dl className="confirmation-details">
            <div><dt>Pickup</dt><dd>{fmt(rental.pickupDateTime)}</dd></div>
            <div><dt>Return</dt><dd>{fmt(rental.returnDateTime)}</dd></div>
            <div><dt>Duration</dt><dd>{rental.numberOfDays} day{rental.numberOfDays !== 1 ? 's' : ''}</dd></div>
            <div><dt>Daily rate</dt><dd>₹{rental.dailyRate}</dd></div>
            <div><dt>Subtotal</dt><dd>₹{rental.subtotal}</dd></div>
            {rental.discountAmount > 0 && (
              <div><dt>Discount{rental.promoCode ? ` (${rental.promoCode})` : ''}</dt><dd>-₹{rental.discountAmount}</dd></div>
            )}
            <div><dt>Tax (18%)</dt><dd>₹{rental.taxAmount}</dd></div>
            <div><dt>Security deposit (20%)</dt><dd>₹{rental.securityDeposit}</dd></div>
            <div className="total-row"><dt>Total</dt><dd>₹{rental.totalAmount}</dd></div>
            {rental.actualReturnDatetime && (
              <div><dt>Actual return</dt><dd>{fmt(rental.actualReturnDatetime)}</dd></div>
            )}
            <div><dt>Booked on</dt><dd>{fmt(rental.createdAt)}</dd></div>
          </dl>
          {rental.notes && <p className="muted small-note">Notes: {rental.notes}</p>}

          <div className="confirmation-actions">
            {canCancel(rental) && (
              <button
                type="button"
                className="button-secondary"
                onClick={() => onCancel(rental)}
                disabled={cancellingId === rental.rentalId}
              >
                {cancellingId === rental.rentalId ? 'Cancelling…' : 'Cancel rental'}
              </button>
            )}
            <Link className="button" to={`/home/vehicles/${rental.vehicleId}`}>View vehicle</Link>
          </div>

          {(rental.rentalStatus === 'ACTIVE' || rental.rentalStatus === 'COMPLETED' ||
            rental.rentalStatus === 'CANCELLED') && (
            <p className="muted small-note">
              {rental.rentalStatus === 'ACTIVE' && 'This rental is in progress and can no longer be cancelled.'}
              {rental.rentalStatus === 'COMPLETED' && 'This rental is completed.'}
              {rental.rentalStatus === 'CANCELLED' && 'This rental was cancelled.'}
            </p>
          )}
        </article>
      ))}
    </section>
  )
}
