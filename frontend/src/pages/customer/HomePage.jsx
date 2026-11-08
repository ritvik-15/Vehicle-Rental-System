import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { getVehicles } from '../../services/vehicleService.js'
import VehicleImage from '../../components/VehicleImage.jsx'
import ErrorAlert from '../../components/ErrorAlert.jsx'

// Enum values from the approved backend (server-side filtering/search/sorting).
const TYPES = ['CAR', 'BIKE', 'BUS', 'TRUCK']
const FUELS = ['PETROL', 'DIESEL', 'ELECTRIC', 'CNG', 'HYBRID']
const TRANSMISSIONS = ['MANUAL', 'AUTOMATIC', 'NOT_APPLICABLE']
// Maps to backend sortBy/sortDir; empty = backend default (createdAt desc).
const SORTS = [
  { key: '', label: 'Newest first', sortBy: '', sortDir: '' },
  { key: 'rate-asc', label: 'Price: low to high', sortBy: 'dailyRate', sortDir: 'asc' },
  { key: 'rate-desc', label: 'Price: high to low', sortBy: 'dailyRate', sortDir: 'desc' },
  { key: 'brand', label: 'Brand: A to Z', sortBy: 'brand', sortDir: 'asc' },
  { key: 'model', label: 'Model: A to Z', sortBy: 'model', sortDir: 'asc' },
]

export default function HomePage() {
  const [searchInput, setSearchInput] = useState('')
  const [locationInput, setLocationInput] = useState('')
  const [search, setSearch] = useState('') // debounced
  const [location, setLocation] = useState('') // debounced
  const [vehicleType, setVehicleType] = useState('')
  const [fuelType, setFuelType] = useState('')
  const [transmission, setTransmission] = useState('')
  const [sortKey, setSortKey] = useState('')

  const [vehicles, setVehicles] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [backendDown, setBackendDown] = useState(false)
  const [refreshTick, setRefreshTick] = useState(0)
  const seqRef = useRef(0) // ignore out-of-order responses

  // Debounce text inputs — no request per keystroke.
  useEffect(() => {
    const t = setTimeout(() => {
      setSearch(searchInput.trim())
      setLocation(locationInput.trim())
    }, 350)
    return () => clearTimeout(t)
  }, [searchInput, locationInput])

  // Single request per criteria change.
  useEffect(() => {
    const seq = ++seqRef.current
    setLoading(true)
    setError('')
    setBackendDown(false)
    const sort = SORTS.find((s) => s.key === sortKey) || SORTS[0]
    getVehicles({
      search,
      vehicleType,
      fuelType,
      transmission,
      location,
      sortBy: sort.sortBy,
      sortDir: sort.sortDir,
    })
      .then((data) => {
        if (seq !== seqRef.current) return // stale response
        setVehicles(Array.isArray(data) ? data : [])
        setLoading(false)
      })
      .catch((err) => {
        if (seq !== seqRef.current) return
        setVehicles([])
        setBackendDown(err?.status === 0)
        setError(err?.message || 'Failed to load vehicles.')
        setLoading(false)
      })
  }, [search, location, vehicleType, fuelType, transmission, sortKey, refreshTick])

  const filtersActive = Boolean(search || location || vehicleType || fuelType || transmission)

  const clearFilters = () => {
    setSearchInput('')
    setLocationInput('')
    setSearch('')
    setLocation('')
    setVehicleType('')
    setFuelType('')
    setTransmission('')
    setSortKey('')
  }

  return (
    <section className="listing">
      <header className="listing-hero">
        <p className="listing-eyebrow">Premium vehicle rental</p>
        <h1>
          Your journey
          <br />
          <em>begins here</em>
        </h1>
        <p className="listing-sub">
          Search the fleet, compare daily rates, and pick the vehicle that fits your trip.
        </p>
        <span className="hero-placeholder">Scenic hero photo — temporary placeholder</span>
      </header>

      <div className="listing-filters">
        <div className="listing-field listing-field-search">
          <label htmlFor="veh-search">Search</label>
          <input
            id="veh-search"
            type="search"
            placeholder="Brand, model…"
            value={searchInput}
            onChange={(e) => setSearchInput(e.target.value)}
          />
          <span className="field-icon" aria-hidden="true">
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
              <circle cx="11" cy="11" r="7" />
              <line x1="16.5" y1="16.5" x2="21" y2="21" />
            </svg>
          </span>
        </div>
        <div className="listing-field">
          <label htmlFor="veh-location">Location</label>
          <input
            id="veh-location"
            type="text"
            placeholder="Exact location e.g. Mumbai"
            value={locationInput}
            onChange={(e) => setLocationInput(e.target.value)}
          />
        </div>
        <div className="listing-field">
          <label htmlFor="veh-type">Type</label>
          <select id="veh-type" value={vehicleType} onChange={(e) => setVehicleType(e.target.value)}>
            <option value="">All types</option>
            {TYPES.map((t) => (
              <option key={t} value={t}>{t}</option>
            ))}
          </select>
        </div>
        <div className="listing-field">
          <label htmlFor="veh-fuel">Fuel</label>
          <select id="veh-fuel" value={fuelType} onChange={(e) => setFuelType(e.target.value)}>
            <option value="">All fuels</option>
            {FUELS.map((f) => (
              <option key={f} value={f}>{f}</option>
            ))}
          </select>
        </div>
        <div className="listing-field">
          <label htmlFor="veh-trans">Transmission</label>
          <select id="veh-trans" value={transmission} onChange={(e) => setTransmission(e.target.value)}>
            <option value="">Any</option>
            {TRANSMISSIONS.map((t) => (
              <option key={t} value={t}>{t}</option>
            ))}
          </select>
        </div>
        <div className="listing-field">
          <label htmlFor="veh-sort">Sort by</label>
          <select id="veh-sort" value={sortKey} onChange={(e) => setSortKey(e.target.value)}>
            {SORTS.map((s) => (
              <option key={s.key} value={s.key}>{s.label}</option>
            ))}
          </select>
        </div>
        {(filtersActive || sortKey) && (
          <button type="button" className="button-secondary listing-clear" onClick={clearFilters}>
            Clear filters
          </button>
        )}
      </div>

      {error && (
        <div>
          <ErrorAlert message={error} />
          <p className="muted">
            {backendDown
              ? 'The backend server appears to be offline. Please try again shortly.'
              : 'You can retry loading the vehicle list.'}
          </p>
          <button type="button" onClick={() => setRefreshTick((t) => t + 1)}>Try again</button>
        </div>
      )}

      {!error && loading && <p className="state-note">Loading vehicles…</p>}

      {!error && !loading && vehicles.length === 0 && (
        <div className="state-note">
          <p>
            {filtersActive
              ? 'No vehicles match your current search or filters.'
              : 'No vehicles are available right now.'}
          </p>
          {filtersActive && (
            <button type="button" className="button-secondary" onClick={clearFilters}>
              Clear filters
            </button>
          )}
        </div>
      )}

      {!error && !loading && vehicles.length > 0 && (
        <div className="listing-fleet">
          <header className="fleet-head">
            <p className="fleet-eyebrow">Our fleet</p>
            <h2>Available vehicles, ready to drive</h2>
            <p className="fleet-sub">
              Compare daily rates, specifications, and availability across the full collection.
            </p>
          </header>
          <p className="muted listing-count">{vehicles.length} vehicle{vehicles.length === 1 ? '' : 's'} found</p>
          <div className="listing-grid">
            {vehicles.map((v) => (
              <article key={v.vehicleId} className="listing-card">
                <Link to={`/home/vehicles/${v.vehicleId}`} className="listing-card-media">
                  <VehicleImage
                    images={v.images}
                    alt={`${v.brand} ${v.model}`}
                    type={v.vehicleType}
                  />
                  <span className="listing-status">
                    <span className={`badge badge-${(v.status || '').toLowerCase()}`}>{v.status}</span>
                  </span>
                </Link>
                <div className="listing-card-body">
                  <h2>
                    <Link to={`/home/vehicles/${v.vehicleId}`}>{v.brand} {v.model}</Link>
                  </h2>
                  <p className="badges">
                    <span className="badge">{v.vehicleType}</span>
                    <span className="badge">{v.fuelType}</span>
                    <span className="badge">{v.transmission}</span>
                  </p>
                  <dl className="vehicle-facts">
                    <div><dt>Location</dt><dd>{v.location}</dd></div>
                    <div><dt>Seats</dt><dd>{v.seatingCapacity}</dd></div>
                  </dl>
                  <div className="listing-card-foot">
                    <p className="rate listing-price">₹{v.dailyRate}<span>/day</span></p>
                    <Link className="button listing-cta" to={`/home/vehicles/${v.vehicleId}`}>View details</Link>
                  </div>
                </div>
              </article>
            ))}
          </div>
        </div>
      )}
    </section>
  )
}
