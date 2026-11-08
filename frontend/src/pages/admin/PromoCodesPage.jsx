import { useEffect, useRef, useState } from 'react'
import {
  createPromoCode,
  listPromoCodes,
  updatePromoCodeStatus,
} from '../../services/adminPromoService.js'
import ErrorAlert from '../../components/ErrorAlert.jsx'

// Phase 13A admin promo-code management.
// Exact backend contracts (verified in AdminPromoController/AdminPromoService):
//   GET   /api/admin/promos  -> 200 List<PromoCodeAdminResponse>
//        { code, discountType, discountValue, minimumAmount, maximumDiscount,
//          startDate, expiryDate, usageLimit, usedCount, status, createdAt }
//        (createdAt DESC; no params/pagination/filters).
//   POST  /api/admin/promos  -> 201 PromoCodeAdminResponse
//        body { code (required, <=50 chars), discountType ("PERCENTAGE"|"FIXED"),
//               discountValue (required, >0; <=100 for PERCENTAGE),
//               minimumAmount (required, >=0), maximumDiscount? (>0),
//               startDate (required), expiryDate (required, after startDate),
//               usageLimit? (whole number >=1), status? ("ACTIVE"|"INACTIVE",
//               defaults to ACTIVE) }
//        400 validation failure, 409 duplicate code. usedCount always starts at 0.
//   PATCH /api/admin/promos/{code}/status -> 200 PromoCodeAdminResponse
//        body { status } — only "ACTIVE" or "INACTIVE"; 400 blank/invalid or
//        unchanged status, 404 unknown code. No other transitions exist.
// There is NO discount-field update and NO delete endpoint — intentionally not
// implemented by the backend, so this page offers neither. Booking-time promo
// validation, row locking, usage consumption and release-on-cancellation/expiry
// remain unchanged backend behavior and are never triggered from this page.
// If the running backend predates these endpoints, the server's own error is
// shown verbatim — that is a deployment gap, not a page defect.
const DISCOUNT_TYPES = ['PERCENTAGE', 'FIXED']
const PROMO_STATUSES = ['ACTIVE', 'INACTIVE']

const EMPTY_FORM = {
  code: '',
  discountType: 'PERCENTAGE',
  discountValue: '',
  minimumAmount: '',
  maximumDiscount: '',
  startDate: '',
  expiryDate: '',
  usageLimit: '',
  status: 'ACTIVE',
}

const fmtDateTime = (value) => (value ? String(value).replace('T', ' ') : '—')
const fmtMoney = (value) =>
  value === null || value === undefined || value === ''
    ? '—'
    : `₹${Number(value).toFixed(2)}`

// Client-side mirror of the server's validation constraints
// (AdminPromoService), so mistakes are caught before a request. The server
// remains authoritative — its messages are shown verbatim whenever it
// rejects a submission.
function validatePromoForm(f) {
  const code = f.code.trim()
  if (!code) return 'Promo code is required.'
  if (code.length > 50) return 'Promo code must not exceed 50 characters.'
  if (!DISCOUNT_TYPES.includes(f.discountType)) return 'Discount type is required.'
  if (f.discountValue === '' || !Number.isFinite(Number(f.discountValue))) {
    return 'Discount value must be a valid number.'
  }
  if (Number(f.discountValue) <= 0) return 'Discount value must be greater than 0.00.'
  if (f.discountType === 'PERCENTAGE' && Number(f.discountValue) > 100) {
    return 'Percentage discount value must be 100 or less.'
  }
  if (f.minimumAmount === '' || !Number.isFinite(Number(f.minimumAmount))) {
    return 'Minimum amount must be a valid number.'
  }
  if (Number(f.minimumAmount) < 0) return 'Minimum amount must be 0.00 or greater.'
  if (f.maximumDiscount !== '') {
    if (!Number.isFinite(Number(f.maximumDiscount))) {
      return 'Maximum discount must be a valid number.'
    }
    if (Number(f.maximumDiscount) <= 0) return 'Maximum discount must be greater than 0.00.'
  }
  if (!f.startDate) return 'Start date is required.'
  if (!f.expiryDate) return 'Expiry date is required.'
  if (f.expiryDate <= f.startDate) return 'Expiry date must be after start date.'
  if (f.usageLimit !== '') {
    if (!Number.isInteger(Number(f.usageLimit))) return 'Usage limit must be a whole number.'
    if (Number(f.usageLimit) < 1) return 'Usage limit must be at least 1.'
  }
  if (!PROMO_STATUSES.includes(f.status)) {
    return 'Status must be ACTIVE or INACTIVE.'
  }
  return ''
}

function toRequestBody(f) {
  return {
    code: f.code.trim().toUpperCase(),
    discountType: f.discountType,
    discountValue: Number(f.discountValue),
    minimumAmount: Number(f.minimumAmount),
    maximumDiscount: f.maximumDiscount === '' ? null : Number(f.maximumDiscount),
    startDate: f.startDate,
    expiryDate: f.expiryDate,
    usageLimit: f.usageLimit === '' ? null : Number(f.usageLimit),
    status: f.status,
  }
}

export default function PromoCodesPage() {
  const [promos, setPromos] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [backendDown, setBackendDown] = useState(false)
  const listSeqRef = useRef(0) // ignore out-of-order list responses

  // Client-side filtering over the complete loaded list (the API offers no
  // search/filter parameters — clearly marked as in-browser below).
  const [search, setSearch] = useState('')
  const [statusFilter, setStatusFilter] = useState('')

  // Create form (list view).
  const [showForm, setShowForm] = useState(false)
  const [form, setForm] = useState(EMPTY_FORM)
  const [creating, setCreating] = useState(false)
  const [createError, setCreateError] = useState('')
  const [createOk, setCreateOk] = useState('')

  // Status change (per promo card; one at a time, keyed by code).
  const [pendingCode, setPendingCode] = useState(null)
  const [statusErrors, setStatusErrors] = useState({})
  const [statusOks, setStatusOks] = useState({})

  const load = () => {
    const seq = ++listSeqRef.current
    setLoading(true)
    setError('')
    setBackendDown(false)
    listPromoCodes()
      .then((data) => {
        if (seq !== listSeqRef.current) return // stale response
        setPromos(Array.isArray(data) ? data : [])
        setLoading(false)
      })
      .catch((err) => {
        if (seq !== listSeqRef.current) return
        setPromos([])
        setBackendDown(err?.status === 0)
        setError(err?.message || 'Failed to load promo codes.')
        setLoading(false)
      })
  }

  useEffect(() => { load() }, [])

  // ---- create form ----
  const setField = (key) => (e) => {
    setForm((f) => ({ ...f, [key]: e.target.value }))
    setCreateError('')
  }

  const openForm = () => {
    setCreateError('')
    setCreateOk('')
    setShowForm(true)
  }

  const cancelForm = () => {
    if (creating) return // no cancelling while a submission is in flight
    setShowForm(false)
    setForm(EMPTY_FORM)
    setCreateError('')
  }

  const onSubmitCreate = async (e) => {
    e.preventDefault()
    if (creating) return // duplicate-submission guard (submit button is also disabled)
    const invalid = validatePromoForm(form)
    if (invalid) {
      setCreateError(invalid)
      return
    }
    setCreating(true)
    setCreateError('')
    setCreateOk('')
    try {
      const created = await createPromoCode(toRequestBody(form))
      // Only after the server confirms success do we close and reset the form.
      setShowForm(false)
      setForm(EMPTY_FORM)
      setCreateOk(
        `Promo code ${created?.code ?? form.code.trim().toUpperCase()} created (status ${created?.status ?? 'ACTIVE'}).`,
      )
      // Refresh the list. A refresh failure surfaces separately in the list
      // error state — its "Try again" reloads the list only, and the success
      // message stays visible so creation is never implied to have failed.
      load()
    } catch (err) {
      // Entered values are preserved so the admin can correct and resubmit.
      setCreateError(err?.message || 'Failed to create the promo code.')
    } finally {
      setCreating(false)
    }
  }

  // ---- status change (activation/deactivation) ----
  // The backend's only valid transition for a promo code is ACTIVE <-> INACTIVE,
  // so each card offers exactly the one valid target as a single action.
  const onToggleStatus = async (promo) => {
    if (pendingCode) return // duplicate-submission guard (buttons also disabled)
    const target = promo.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'
    if (target === 'INACTIVE') {
      const confirmed = window.confirm(
        `Deactivate promo code ${promo.code}? It will no longer apply to new bookings.`,
      )
      if (!confirmed) return
    }
    setPendingCode(promo.code)
    setStatusErrors((m) => ({ ...m, [promo.code]: '' }))
    setStatusOks((m) => ({ ...m, [promo.code]: '' }))
    try {
      const changed = await updatePromoCodeStatus(promo.code, target)
      setStatusOks((m) => ({
        ...m,
        [promo.code]: `Status of promo code ${promo.code} changed to ${changed?.status || target}.`,
      }))
      // Refresh the list (a refresh failure surfaces in the list error state,
      // whose retry only re-runs the GET — the success message stays visible).
      load()
    } catch (err) {
      // The list keeps showing the current server state so the admin can retry.
      setStatusErrors((m) => ({
        ...m,
        [promo.code]: err?.message || 'Failed to update the promo code status.',
      }))
    } finally {
      setPendingCode(null)
    }
  }

  // ---- list view ----
  const query = search.trim().toLowerCase()
  const filtered = promos.filter((p) => {
    if (statusFilter && p.status !== statusFilter) return false
    if (!query) return true
    return [
      String(p.code ?? ''),
      String(p.discountType ?? ''),
      String(p.status ?? ''),
    ].some((v) => (v ?? '').toLowerCase().includes(query))
  })

  return (
    <section>
      <h1>Promo Codes</h1>

      <p className="muted small-note">
        Promo codes are created and deactivated here and applied by customers at
        booking time. The backend remains authoritative for discount calculation,
        usage limits, date ranges and expiry — this page never overrides those rules.
      </p>

      <p>
        <button type="button" onClick={openForm} disabled={showForm}>
          {showForm ? 'New promo code…' : 'New promo code'}
        </button>
      </p>

      {showForm && (
        <div className="card">
          <h2>Create promo code</h2>
          <form onSubmit={onSubmitCreate} noValidate>
            <div className="control">
              <label htmlFor="promo-create-code">Promo code</label>
              <input
                id="promo-create-code"
                value={form.code}
                onChange={setField('code')}
                maxLength={50}
                placeholder="e.g. COLLEGE20"
                required
              />
            </div>
            <div className="control">
              <label htmlFor="promo-create-type">Discount type</label>
              <select
                id="promo-create-type"
                value={form.discountType}
                onChange={setField('discountType')}
                required
              >
                {DISCOUNT_TYPES.map((t) => (
                  <option key={t} value={t}>
                    {t === 'PERCENTAGE' ? 'Percentage (%)' : 'Fixed amount'}
                  </option>
                ))}
              </select>
            </div>
            <div className="control">
              <label htmlFor="promo-create-value">Discount value</label>
              <input
                id="promo-create-value"
                type="number"
                min="0.01"
                step="0.01"
                value={form.discountValue}
                onChange={setField('discountValue')}
                required
              />
            </div>
            <div className="control">
              <label htmlFor="promo-create-min">Minimum amount</label>
              <input
                id="promo-create-min"
                type="number"
                min="0"
                step="0.01"
                value={form.minimumAmount}
                onChange={setField('minimumAmount')}
                required
              />
            </div>
            <div className="control">
              <label htmlFor="promo-create-max">Maximum discount (optional)</label>
              <input
                id="promo-create-max"
                type="number"
                min="0.01"
                step="0.01"
                value={form.maximumDiscount}
                onChange={setField('maximumDiscount')}
              />
            </div>
            <div className="control">
              <label htmlFor="promo-create-start">Start date</label>
              <input
                id="promo-create-start"
                type="date"
                value={form.startDate}
                onChange={setField('startDate')}
                required
              />
            </div>
            <div className="control">
              <label htmlFor="promo-create-expiry">Expiry date</label>
              <input
                id="promo-create-expiry"
                type="date"
                value={form.expiryDate}
                onChange={setField('expiryDate')}
                required
              />
            </div>
            <div className="control">
              <label htmlFor="promo-create-usage">Usage limit (optional)</label>
              <input
                id="promo-create-usage"
                type="number"
                min="1"
                step="1"
                value={form.usageLimit}
                onChange={setField('usageLimit')}
              />
            </div>
            <div className="control">
              <label htmlFor="promo-create-status">Status</label>
              <select
                id="promo-create-status"
                value={form.status}
                onChange={setField('status')}
                required
              >
                {PROMO_STATUSES.map((s) => (
                  <option key={s} value={s}>{s}</option>
                ))}
              </select>
            </div>
            <p className="muted small-note">
              Percentage values must be 100 or less. The expiry date must be after the
              start date. New codes start with zero uses.
            </p>
            <ErrorAlert message={createError} />
            <button type="submit" className="button-success" disabled={creating}>
              {creating ? 'Creating…' : 'Create promo code'}
            </button>
            <button type="button" className="button-secondary" onClick={cancelForm} disabled={creating}>
              Cancel
            </button>
          </form>
        </div>
      )}

      {createOk && <p className="success-alert" role="status">{createOk}</p>}

      <div className="controls">
        <div className="control">
          <label htmlFor="promo-search">Search (code, type, status)</label>
          <input
            id="promo-search"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Type to filter…"
          />
        </div>
        <div className="control">
          <label htmlFor="promo-filter">Status</label>
          <select id="promo-filter" value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
            <option value="">All statuses</option>
            {PROMO_STATUSES.map((s) => (
              <option key={s} value={s}>{s}</option>
            ))}
          </select>
        </div>
      </div>
      <p className="muted small-note">
        Searching and filtering run in your browser over the complete list loaded from the server.
      </p>

      {loading && <p className="state-note">Loading promo codes…</p>}

      {!loading && (
        <>
          <ErrorAlert message={error} />
          {backendDown && error && (
            <p className="muted">The backend server appears to be offline. Please try again shortly.</p>
          )}
          {error && <button type="button" onClick={load}>Try again</button>}
        </>
      )}

      {!loading && !error && promos.length === 0 && (
        <div className="state-note">
          <p>No promo codes found.</p>
        </div>
      )}

      {!loading && !error && promos.length > 0 && filtered.length === 0 && (
        <div className="state-note">
          <p>No promo codes match the current search or filter.</p>
        </div>
      )}

      {!loading && !error && filtered.length > 0 && (
        <>
          <p className="muted">
            {filtered.length} of {promos.length} promo code{promos.length === 1 ? '' : 's'}
          </p>
          {filtered.map((p) => (
            <article key={p.code} className="card">
              <h3>
                {p.code ?? '—'} <span className="badge">{p.status ?? '—'}</span>
              </h3>
              <p className="rate">
                {p.discountType === 'PERCENTAGE'
                  ? `${p.discountValue ?? '—'}% off`
                  : `${fmtMoney(p.discountValue)} off`}{' '}
                · minimum {fmtMoney(p.minimumAmount)}
                {p.maximumDiscount != null ? ` · capped at ${fmtMoney(p.maximumDiscount)}` : ''}
              </p>
              <dl className="vehicle-facts">
                <div><dt>Discount</dt><dd>{p.discountType === 'PERCENTAGE' ? `${p.discountValue ?? '—'}%` : fmtMoney(p.discountValue)}</dd></div>
                <div><dt>Minimum amount</dt><dd>{fmtMoney(p.minimumAmount)}</dd></div>
                <div><dt>Maximum discount</dt><dd>{p.maximumDiscount != null ? fmtMoney(p.maximumDiscount) : '—'}</dd></div>
                <div><dt>Starts</dt><dd>{p.startDate ?? '—'}</dd></div>
                <div><dt>Expires</dt><dd>{p.expiryDate ?? '—'}</dd></div>
                <div><dt>Usage</dt><dd>{p.usedCount ?? 0} of {p.usageLimit != null ? p.usageLimit : 'unlimited'}</dd></div>
                <div><dt>Created</dt><dd>{fmtDateTime(p.createdAt)}</dd></div>
              </dl>
              <button
                type="button"
                onClick={() => onToggleStatus(p)}
                disabled={pendingCode !== null}
              >
                {p.status === 'ACTIVE' ? 'Deactivate' : 'Activate'}
              </button>
              {statusErrors[p.code] && <ErrorAlert message={statusErrors[p.code]} />}
              {statusOks[p.code] && <p className="success-alert" role="status">{statusOks[p.code]}</p>}
            </article>
          ))}
        </>
      )}
    </section>
  )
}
