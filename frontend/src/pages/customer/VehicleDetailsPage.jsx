import { useEffect, useRef, useState } from 'react'
import { Link, useParams, useNavigate } from 'react-router-dom'
import { checkAvailability, getVehicle } from '../../services/vehicleService.js'
import VehicleImage from '../../components/VehicleImage.jsx'
import ErrorAlert from '../../components/ErrorAlert.jsx'

export default function VehicleDetailsPage() {
  const { id } = useParams()
  const [vehicle, setVehicle] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [notFound, setNotFound] = useState(false)
  const [backendDown, setBackendDown] = useState(false)
  const [retryTick, setRetryTick] = useState(0)

  const [pickup, setPickup] = useState('')
  const [ret, setRet] = useState('')
  const [checkError, setCheckError] = useState('')
  const [result, setResult] = useState(null) // AvailabilityResponse or null
  const [checking, setChecking] = useState(false)
  const checkingRef = useRef(false) // synchronous guard vs repeated clicks
  const seqRef = useRef(0) // invalidates stale in-flight checks

  const navigate = useNavigate()

  const onBook = () => {
    if (!result?.available) return
    // Navigate to booking page with current dates in query params
    const params = new URLSearchParams({
      pickupDateTime: pickup,
      returnDateTime: ret
    })
    navigate(`/home/vehicles/${id}/book?${params.toString()}`)
  }

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError('')
    setNotFound(false)
    setBackendDown(false)
    getVehicle(id)
      .then((data) => {
        if (cancelled) return
        setVehicle(data)
        setLoading(false)
      })
      .catch((err) => {
        if (cancelled) return
        setVehicle(null)
        setLoading(false)
        if (err?.status === 404) {
          setNotFound(true)
          setError('Vehicle not found. It may have been removed or is not available.')
        } else {
          setBackendDown(err?.status === 0)
          setError(err?.message || 'Failed to load the vehicle.')
        }
      })
    return () => { cancelled = true }
  }, [id, retryTick])

  // Client-side sanity checks; backend remains authoritative for the real answer.
  function validateDates() {
    if (!pickup) return 'Select a pickup date and time.'
    if (!ret) return 'Select a return date and time.'
    const p = new Date(pickup)
    const r = new Date(ret)
    if (Number.isNaN(p.getTime())) return 'Pickup date and time is invalid.'
    if (Number.isNaN(r.getTime())) return 'Return date and time is invalid.'
    if (p.getTime() <= Date.now()) return 'Pickup date and time must be in the future.'
    if (r.getTime() <= p.getTime()) return 'Return must be after pickup.'
    return ''
  }

  const onDateChange = (setter) => (e) => {
    setter(e.target.value)
    // A previous result must never be presented as the answer for new dates.
    setResult(null)
    setCheckError('')
  }

  const onCheck = async () => {
    if (checkingRef.current) return // ignore repeated clicks
    const problem = validateDates()
    setCheckError(problem)
    if (problem) return
    checkingRef.current = true
    setChecking(true)
    setResult(null)
    const seq = ++seqRef.current
    try {
      const data = await checkAvailability(id, pickup, ret)
      if (seq === seqRef.current) setResult(data)
    } catch (err) {
      if (seq === seqRef.current) {
        setBackendDown(err?.status === 0)
        setCheckError(err?.message || 'Availability check failed.')
      }
    } finally {
      checkingRef.current = false
      setChecking(false)
    }
  }

  if (loading) return <p className="state-note">Loading vehicle…</p>

  return (
    <section>
      <p>
        <Link to="/home" className="back-link">← Back to Vehicles</Link>
      </p>

      {error && (
        <div>
          <ErrorAlert message={error} />
          {backendDown && (
            <p className="muted">The backend server appears to be offline. Please try again shortly.</p>
          )}
          {!notFound && (
            <button type="button" onClick={() => setRetryTick((t) => t + 1)}>Try again</button>
          )}
        </div>
      )}

      {!error && vehicle && (
        <div className="detail-layout">
          <div className="detail-media">
            <VehicleImage
              images={vehicle.images}
              alt={`${vehicle.brand} ${vehicle.model}`}
              className="detail-main-image"
            />
            {Array.isArray(vehicle.images) && vehicle.images.length > 1 && (
              <p className="muted">{vehicle.images.length} photos</p>
            )}
          </div>

          <div className="detail-info card">
            <h1>{vehicle.brand} {vehicle.model}</h1>
            <p className="badges">
              <span className="badge">{vehicle.vehicleType}</span>
              <span className="badge">{vehicle.fuelType}</span>
              <span className="badge">{vehicle.transmission}</span>
              <span className={`badge badge-${(vehicle.status || '').toLowerCase()}`}>{vehicle.status}</span>
            </p>
            <p className="rate">₹{vehicle.dailyRate}<span>/day</span></p>
            {vehicle.description && <p>{vehicle.description}</p>}
            <dl className="vehicle-facts detail-facts">
              <div><dt>Registration</dt><dd>{vehicle.registrationNumber}</dd></div>
              <div><dt>Seats</dt><dd>{vehicle.seatingCapacity}</dd></div>
              <div><dt>Location</dt><dd>{vehicle.location}</dd></div>
              <div><dt>Fuel</dt><dd>{vehicle.fuelType}</dd></div>
              <div><dt>Transmission</dt><dd>{vehicle.transmission}</dd></div>
              <div><dt>Status</dt><dd>{vehicle.status}</dd></div>
            </dl>
          </div>

          <div className="card availability-card">
            <h2>Check availability</h2>
            <p className="muted">Choose your pickup and return date and time.</p>
            <div className="availability-form">
              <div className="control">
                <label htmlFor="pickup-dt">Pickup date &amp; time</label>
                <input
                  id="pickup-dt"
                  type="datetime-local"
                  value={pickup}
                  onChange={onDateChange(setPickup)}
                />
              </div>
              <div className="control">
                <label htmlFor="return-dt">Return date &amp; time</label>
                <input
                  id="return-dt"
                  type="datetime-local"
                  value={ret}
                  onChange={onDateChange(setRet)}
                />
              </div>
              <button type="button" onClick={onCheck} disabled={checking}>
                {checking ? 'Checking…' : 'Check availability'}
              </button>
            </div>

            {checkError && <ErrorAlert message={checkError} />}

            {result && (
              <p
                className={result.available ? 'success-alert' : 'error-alert'}
                role="status"
                aria-live="polite"
              >
                {result.message}
                <span className="muted">
                  {' '}(vehicle status: {result.vehicleStatus})
                </span>
              </p>
            )}

            {result?.available && (
              <button type="button" className="button" onClick={onBook}>
                Continue to Booking
              </button>
            )}

            <p className="muted small-note">
              Checking availability never creates a booking.
            </p>
          </div>
        </div>
      )}
    </section>
  )
}
