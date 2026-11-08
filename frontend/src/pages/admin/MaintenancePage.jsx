import { useEffect, useRef, useState } from 'react'
import { api } from '../../services/api.js'
import ErrorAlert from '../../components/ErrorAlert.jsx'

// Phase 10 Step 9 admin maintenance management.
// Exact backend contracts (verified in AdminMaintenanceController/MaintenanceService):
//   GET   /api/admin/maintenance       -> 200 List<MaintenanceResponse>
//                                        (maintenanceDate DESC; no params/pagination/filters)
//   POST  /api/admin/maintenance       -> 201 MaintenanceResponse
//        body { vehicleId, maintenanceType, description?, maintenanceDate,
//               completionDate?, cost, serviceProvider?, notes? }
//        400 validation or completionDate < maintenanceDate, 404 unknown vehicle.
//        New records always start as SCHEDULED (server-set; status not accepted here).
//   GET   /api/admin/maintenance/{id}  -> 200 MaintenanceResponse (404 unknown)
//   PUT   /api/admin/maintenance/{id}  -> 200 MaintenanceResponse
//        same 8 fields as POST (400/404 as above); status is NOT editable via PUT.
//   PATCH /api/admin/maintenance/{id}/status -> 200 MaintenanceResponse
//        body { status } — MaintenanceStatus valueOf (case-insensitive): SCHEDULED,
//        IN_PROGRESS, COMPLETED, CANCELLED; 400 blank/invalid, 404 unknown.
//        No transition matrix exists: any status may be set to any other value.
// There is NO delete endpoint — deletion is intentionally not implemented.
//
// Vehicle-availability rules (verified in MaintenanceService/RentalService):
// maintenance records NEVER change vehicle status — nothing in the backend sets
// VehicleStatus.MAINTENANCE (only AVAILABLE/INACTIVE can be set manually, via
// the vehicles page). A SCHEDULED/IN_PROGRESS record overlapping a requested
// date range blocks new bookings server-side ("Vehicle is under maintenance
// during the requested period"), and completing/cancelling a record lifts that
// block purely through the record's own status/dates. This page therefore does
// not offer any vehicle-status control, and does not claim that completing
// maintenance makes a vehicle AVAILABLE.
//
// Vehicle picker for create/edit lazily loads GET /api/admin/vehicles (verified
// admin list endpoint) — required because both POST and PUT demand a vehicleId.
const fmt = (value) => (value ? String(value).replace('T', ' ') : '—')
const STATUSES = ['SCHEDULED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED']

const EMPTY_FORM = {
  vehicleId: '',
  maintenanceType: '',
  description: '',
  maintenanceDate: '',
  completionDate: '',
  cost: '',
  serviceProvider: '',
  notes: '',
}

// Client-side mirror of the server's validation constraints, so mistakes are
// caught before a request. The server remains authoritative — its messages are
// shown verbatim whenever it rejects a submission.
function validateMaintenanceForm(f) {
  if (!f.vehicleId) return 'Vehicle is required.'
  if (!f.maintenanceType.trim()) return 'Maintenance type is required.'
  if (f.maintenanceType.trim().length > 100) return 'Maintenance type must not exceed 100 characters.'
  if (f.description.length > 1000) return 'Description must not exceed 1000 characters.'
  if (!f.maintenanceDate) return 'Maintenance date is required.'
  if (f.completionDate && f.completionDate < f.maintenanceDate) {
    return 'Completion date cannot be before maintenance date.'
  }
  if (f.cost.trim() === '' || !Number.isFinite(Number(f.cost))) return 'Cost must be a valid number.'
  if (Number(f.cost) < 0) return 'Cost must be 0.00 or greater.'
  if (f.serviceProvider.length > 150) return 'Service provider must not exceed 150 characters.'
  if (f.notes.length > 1000) return 'Notes must not exceed 1000 characters.'
  return ''
}

function toRequestBody(f) {
  return {
    vehicleId: Number(f.vehicleId),
    maintenanceType: f.maintenanceType.trim(),
    description: f.description.trim() || null,
    maintenanceDate: f.maintenanceDate,
    completionDate: f.completionDate || null,
    cost: Number(f.cost),
    serviceProvider: f.serviceProvider.trim() || null,
    notes: f.notes.trim() || null,
  }
}

export default function MaintenancePage() {
  const [records, setRecords] = useState([])
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

  // Vehicle picker (create/edit forms) — lazily loaded, needed for vehicleId.
  const [vehicles, setVehicles] = useState(null) // null = not loaded yet
  const [vehiclesLoading, setVehiclesLoading] = useState(false)
  const [vehiclesError, setVehiclesError] = useState('')
  const vehiclesSeqRef = useRef(0) // ignore out-of-order vehicle responses

  // Create form (list view).
  const [showForm, setShowForm] = useState(false)
  const [form, setForm] = useState(EMPTY_FORM)
  const [creating, setCreating] = useState(false)
  const [createError, setCreateError] = useState('')
  const [createOk, setCreateOk] = useState('')

  // Edit form (detail view; null = viewing).
  const [editForm, setEditForm] = useState(null)
  const [updating, setUpdating] = useState(false)
  const [updateError, setUpdateError] = useState('')
  const [updateOk, setUpdateOk] = useState('')

  // Status change (detail view).
  const [statusChoice, setStatusChoice] = useState('')
  const [statusUpdating, setStatusUpdating] = useState(false)
  const [statusError, setStatusError] = useState('')
  const [statusOk, setStatusOk] = useState('')

  const load = () => {
    const seq = ++listSeqRef.current
    setLoading(true)
    setError('')
    setBackendDown(false)
    api.get('/api/admin/maintenance')
      .then((data) => {
        if (seq !== listSeqRef.current) return // stale response
        setRecords(Array.isArray(data) ? data : [])
        setLoading(false)
      })
      .catch((err) => {
        if (seq !== listSeqRef.current) return
        setRecords([])
        setBackendDown(err?.status === 0)
        setError(err?.message || 'Failed to load maintenance records.')
        setLoading(false)
      })
  }

  useEffect(() => { load() }, [])

  const loadVehicles = () => {
    if (vehiclesLoading) return // duplicate-request guard
    const seq = ++vehiclesSeqRef.current
    setVehiclesLoading(true)
    setVehiclesError('')
    api.get('/api/admin/vehicles')
      .then((data) => {
        if (seq !== vehiclesSeqRef.current) return // stale response
        setVehicles(Array.isArray(data) ? data : [])
      })
      .catch((err) => {
        if (seq !== vehiclesSeqRef.current) return
        setVehiclesError(err?.message || 'Failed to load vehicles for the picker.')
      })
      .finally(() => {
        if (seq === vehiclesSeqRef.current) setVehiclesLoading(false)
      })
  }

  const openDetail = (maintenanceId) => {
    const seq = ++detailSeqRef.current
    setSelectedId(maintenanceId)
    setDetail(null)
    setDetailError('')
    setDetailDown(false)
    setDetailLoading(true)
    setEditForm(null)
    setUpdateError('')
    setUpdateOk('')
    setStatusChoice('')
    setStatusError('')
    setStatusOk('')
    api.get(`/api/admin/maintenance/${maintenanceId}`)
      .then((data) => {
        if (seq === detailSeqRef.current) setDetail(data)
      })
      .catch((err) => {
        if (seq !== detailSeqRef.current) return
        setDetailDown(err?.status === 0)
        setDetailError(err?.message || 'Failed to load the maintenance record.')
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
    setEditForm(null)
    setUpdateError('')
    setUpdateOk('')
    setStatusChoice('')
    setStatusError('')
    setStatusOk('')
  }

  // ---- create form ----
  const setField = (key) => (e) => {
    setForm((f) => ({ ...f, [key]: e.target.value }))
    setCreateError('')
  }

  const openForm = () => {
    setCreateError('')
    setCreateOk('')
    setShowForm(true)
    if (vehicles == null) loadVehicles() // picker needed for the required vehicleId
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
    const invalid = validateMaintenanceForm(form)
    if (invalid) {
      setCreateError(invalid)
      return
    }
    setCreating(true)
    setCreateError('')
    setCreateOk('')
    try {
      const created = await api.post('/api/admin/maintenance', toRequestBody(form))
      // Only after the server confirms success do we close and reset the form.
      setShowForm(false)
      setForm(EMPTY_FORM)
      setCreateOk(
        created?.maintenanceId != null
          ? `Maintenance record #${created.maintenanceId} created (status ${created.status ?? 'SCHEDULED'}).`
          : 'Maintenance record created.',
      )
      // Refresh the list. A refresh failure surfaces separately in the list
      // error state — its "Try again" reloads the list only, and the success
      // message stays visible so creation is never implied to have failed.
      load()
    } catch (err) {
      // Entered values are preserved so the admin can correct and resubmit.
      setCreateError(err?.message || 'Failed to create the maintenance record.')
    } finally {
      setCreating(false)
    }
  }

  // ---- edit form (detail view) ----
  const openEdit = () => {
    if (!detail || updating) return
    setUpdateError('')
    setUpdateOk('')
    if (vehicles == null) loadVehicles()
    // Prefill every editable field from the loaded detail so unchanged
    // required values are always submitted back to the backend. Status is
    // deliberately absent — PUT does not accept it.
    setEditForm({
      vehicleId: detail.vehicleId != null ? String(detail.vehicleId) : '',
      maintenanceType: detail.maintenanceType ?? '',
      description: detail.description ?? '',
      maintenanceDate: detail.maintenanceDate ?? '',
      completionDate: detail.completionDate ?? '',
      cost: detail.cost != null ? String(detail.cost) : '',
      serviceProvider: detail.serviceProvider ?? '',
      notes: detail.notes ?? '',
    })
  }

  const setEditField = (key) => (e) => {
    setEditForm((f) => ({ ...f, [key]: e.target.value }))
    setUpdateError('')
  }

  const cancelEdit = () => {
    if (updating) return
    setEditForm(null)
    setUpdateError('')
  }

  const onSubmitUpdate = async (e) => {
    e.preventDefault()
    if (updating || !editForm) return // duplicate-submission guard
    const invalid = validateMaintenanceForm(editForm)
    if (invalid) {
      setUpdateError(invalid)
      return
    }
    setUpdating(true)
    setUpdateError('')
    setUpdateOk('')
    try {
      await api.put(`/api/admin/maintenance/${selectedId}`, toRequestBody(editForm))
      setEditForm(null)
      setUpdateOk('Maintenance record updated.')
      // Refresh list and detail with the existing fetch logic. A refresh
      // failure surfaces in the list/detail error states whose retry only
      // re-runs GETs — the success message stays visible, so the update is
      // never reported as failed and resubmission is not encouraged.
      load()
      openDetail(selectedId)
    } catch (err) {
      // Keep the current values so the admin can correct and retry.
      setUpdateError(err?.message || 'Failed to update the maintenance record.')
    } finally {
      setUpdating(false)
    }
  }

  // ---- status change (detail view) ----
  const setStatusField = (e) => {
    setStatusChoice(e.target.value)
    setStatusError('')
  }

  const onSubmitStatus = async (e) => {
    e.preventDefault()
    if (statusUpdating || !detail) return // duplicate-submission guard (button also disabled)
    const target = (statusChoice || '').trim().toUpperCase()
    if (!STATUSES.includes(target)) {
      setStatusError('Select a status to apply.')
      return
    }
    if (target === detail.status) return // never send a no-op change
    if (target === 'CANCELLED') {
      const confirmed = window.confirm(
        `Set maintenance record #${detail.maintenanceId} to CANCELLED? The backend allows changing it back at any time.`,
      )
      if (!confirmed) return
    }
    setStatusUpdating(true)
    setStatusError('')
    setStatusOk('')
    try {
      const changed = await api.patch(`/api/admin/maintenance/${selectedId}/status`, { status: target })
      setStatusChoice('')
      setStatusOk(`Status of maintenance record #${selectedId} changed to ${changed?.status || target}.`)
      // Refresh list and detail (refresh failure never retracts the success).
      load()
      openDetail(selectedId)
    } catch (err) {
      // Keep the current choice so the admin can correct and retry.
      setStatusError(err?.message || 'Failed to update the status.')
    } finally {
      setStatusUpdating(false)
    }
  }

  // ---- detail view ----
  if (selectedId !== null) {
    const busy = updating || statusUpdating
    return (
      <section>
        <p>
          <button type="button" className="button-secondary" onClick={closeDetail} disabled={busy}>
            ← Back to maintenance
          </button>
        </p>

        {updateOk && <p className="success-alert" role="status">{updateOk}</p>}
        {statusOk && <p className="success-alert" role="status">{statusOk}</p>}

        {detailLoading && <p className="state-note">Loading maintenance record…</p>}

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
              <h1>
                Maintenance #{detail.maintenanceId}{' '}
                <span className="badge">{detail.status ?? '—'}</span>
              </h1>
              <p className="rate">
                {detail.maintenanceType || 'Maintenance'} · ₹{detail.cost ?? '—'}
              </p>
              <dl className="confirmation-details">
                <div>
                  <dt>Vehicle</dt>
                  <dd>
                    {detail.vehicleBrand} {detail.vehicleModel} ({detail.vehicleRegistrationNumber ?? '—'})
                  </dd>
                </div>
                <div><dt>Vehicle ID</dt><dd>{detail.vehicleId ?? '—'}</dd></div>
                <div><dt>Status</dt><dd>{detail.status ?? '—'}</dd></div>
                <div><dt>Maintenance date</dt><dd>{fmt(detail.maintenanceDate)}</dd></div>
                <div><dt>Completion date</dt><dd>{fmt(detail.completionDate)}</dd></div>
                <div><dt>Cost</dt><dd>₹{detail.cost ?? '—'}</dd></div>
                <div><dt>Service provider</dt><dd>{detail.serviceProvider ?? '—'}</dd></div>
                <div><dt>Description</dt><dd>{detail.description ?? '—'}</dd></div>
                <div><dt>Notes</dt><dd>{detail.notes ?? '—'}</dd></div>
                <div><dt>Created</dt><dd>{fmt(detail.createdAt)}</dd></div>
                <div><dt>Last updated</dt><dd>{fmt(detail.updatedAt)}</dd></div>
              </dl>
            </div>

            <div className="card">
              <h2>Change status</h2>
              <p>
                Current status: <span className="badge">{detail.status}</span>
              </p>
              <p className="muted small-note">
                The backend accepts any of SCHEDULED, IN_PROGRESS, COMPLETED, CANCELLED at any time.
                Status changes here do not alter the vehicle&apos;s status — overlapping
                SCHEDULED/IN_PROGRESS records are what block new bookings (server-enforced).
              </p>
              <form onSubmit={onSubmitStatus} noValidate>
                <div className="control">
                  <label htmlFor="mnt-status">Set status to</label>
                  <select id="mnt-status" value={statusChoice} onChange={setStatusField}>
                    <option value="">Select status</option>
                    {STATUSES.filter((s) => s !== detail.status).map((s) => (
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
              <h2>Edit record</h2>
              {!editForm && (
                <button type="button" onClick={openEdit} disabled={busy}>Edit maintenance details</button>
              )}
              {editForm && (
                <form onSubmit={onSubmitUpdate} noValidate>
                  <div className="control">
                    <label htmlFor="veh-edit-vehicle">Vehicle</label>
                    <select
                      id="veh-edit-vehicle"
                      value={editForm.vehicleId}
                      onChange={setEditField('vehicleId')}
                      required
                      disabled={vehiclesLoading}
                    >
                      <option value="">
                        {vehiclesLoading ? 'Loading vehicles…' : 'Select a vehicle'}
                      </option>
                      {Array.isArray(vehicles) && vehicles.map((v) => (
                        <option key={v.vehicleId} value={String(v.vehicleId)}>
                          {v.registrationNumber} — {v.brand} {v.model}
                        </option>
                      ))}
                    </select>
                  </div>
                  <ErrorAlert message={vehiclesError} />
                  {vehiclesError && (
                    <button type="button" onClick={loadVehicles} disabled={vehiclesLoading}>Retry loading vehicles</button>
                  )}
                  <div className="control">
                    <label htmlFor="veh-edit-type">Maintenance type</label>
                    <input
                      id="veh-edit-type"
                      value={editForm.maintenanceType}
                      onChange={setEditField('maintenanceType')}
                      maxLength={100}
                      required
                    />
                  </div>
                  <div className="control">
                    <label htmlFor="veh-edit-desc">Description (optional)</label>
                    <textarea
                      id="veh-edit-desc"
                      value={editForm.description}
                      onChange={setEditField('description')}
                      maxLength={1000}
                      rows={3}
                    />
                  </div>
                  <div className="control">
                    <label htmlFor="veh-edit-date">Maintenance date</label>
                    <input
                      id="veh-edit-date"
                      type="date"
                      value={editForm.maintenanceDate}
                      onChange={setEditField('maintenanceDate')}
                      required
                    />
                  </div>
                  <div className="control">
                    <label htmlFor="veh-edit-completion">Completion date (optional)</label>
                    <input
                      id="veh-edit-completion"
                      type="date"
                      value={editForm.completionDate}
                      onChange={setEditField('completionDate')}
                    />
                  </div>
                  <div className="control">
                    <label htmlFor="veh-edit-cost">Cost (₹)</label>
                    <input
                      id="veh-edit-cost"
                      type="number"
                      min="0"
                      step="0.01"
                      value={editForm.cost}
                      onChange={setEditField('cost')}
                      required
                    />
                  </div>
                  <div className="control">
                    <label htmlFor="veh-edit-provider">Service provider (optional)</label>
                    <input
                      id="veh-edit-provider"
                      value={editForm.serviceProvider}
                      onChange={setEditField('serviceProvider')}
                      maxLength={150}
                    />
                  </div>
                  <div className="control">
                    <label htmlFor="veh-edit-notes">Notes (optional)</label>
                    <textarea
                      id="veh-edit-notes"
                      value={editForm.notes}
                      onChange={setEditField('notes')}
                      maxLength={1000}
                      rows={3}
                    />
                  </div>
                  <ErrorAlert message={updateError} />
                  <button type="submit" className="button-success" disabled={updating}>
                    {updating ? 'Saving…' : 'Save changes'}
                  </button>
                  <button type="button" className="button-secondary" onClick={cancelEdit} disabled={updating}>
                    Cancel
                  </button>
                </form>
              )}
            </div>
          </>
        )}
      </section>
    )
  }

  // ---- list view ----
  const query = search.trim().toLowerCase()
  const filtered = records.filter((r) => {
    if (statusFilter && r.status !== statusFilter) return false
    if (!query) return true
    return [
      String(r.maintenanceId ?? ''),
      String(r.vehicleId ?? ''),
      r.vehicleRegistrationNumber,
      r.vehicleBrand,
      r.vehicleModel,
      r.maintenanceType,
      r.description,
      r.serviceProvider,
      r.notes,
    ].some((v) => (v ?? '').toLowerCase().includes(query))
  })

  return (
    <section>
      <h1>Maintenance</h1>

      <p className="muted small-note">
        Maintenance records never change a vehicle&apos;s status. Overlapping SCHEDULED or IN_PROGRESS
        records block new bookings for those dates (enforced by the backend). Deletion of records is
        not supported by the backend.
      </p>

      <p>
        <button type="button" onClick={openForm} disabled={showForm}>
          {showForm ? 'New maintenance record…' : 'New maintenance record'}
        </button>
      </p>

      {showForm && (
        <div className="card">
          <h2>Schedule maintenance</h2>
          <form onSubmit={onSubmitCreate} noValidate>
            <div className="control">
              <label htmlFor="veh-create-vehicle">Vehicle</label>
              <select
                id="veh-create-vehicle"
                value={form.vehicleId}
                onChange={setField('vehicleId')}
                required
                disabled={vehiclesLoading}
              >
                <option value="">
                  {vehiclesLoading ? 'Loading vehicles…' : 'Select a vehicle'}
                </option>
                {Array.isArray(vehicles) && vehicles.map((v) => (
                  <option key={v.vehicleId} value={String(v.vehicleId)}>
                    {v.registrationNumber} — {v.brand} {v.model}
                  </option>
                ))}
              </select>
            </div>
            <ErrorAlert message={vehiclesError} />
            {vehiclesError && (
              <button type="button" onClick={loadVehicles} disabled={vehiclesLoading}>Retry loading vehicles</button>
            )}
            <div className="control">
              <label htmlFor="veh-create-type">Maintenance type</label>
              <input
                id="veh-create-type"
                value={form.maintenanceType}
                onChange={setField('maintenanceType')}
                maxLength={100}
                placeholder="e.g. Oil change"
                required
              />
            </div>
            <div className="control">
              <label htmlFor="veh-create-desc">Description (optional)</label>
              <textarea
                id="veh-create-desc"
                value={form.description}
                onChange={setField('description')}
                maxLength={1000}
                rows={3}
              />
            </div>
            <div className="control">
              <label htmlFor="veh-create-date">Maintenance date</label>
              <input
                id="veh-create-date"
                type="date"
                value={form.maintenanceDate}
                onChange={setField('maintenanceDate')}
                required
              />
            </div>
            <div className="control">
              <label htmlFor="veh-create-completion">Completion date (optional)</label>
              <input
                id="veh-create-completion"
                type="date"
                value={form.completionDate}
                onChange={setField('completionDate')}
              />
            </div>
            <div className="control">
              <label htmlFor="veh-create-cost">Cost (₹)</label>
              <input
                id="veh-create-cost"
                type="number"
                min="0"
                step="0.01"
                value={form.cost}
                onChange={setField('cost')}
                required
              />
            </div>
            <div className="control">
              <label htmlFor="veh-create-provider">Service provider (optional)</label>
              <input
                id="veh-create-provider"
                value={form.serviceProvider}
                onChange={setField('serviceProvider')}
                maxLength={150}
              />
            </div>
            <div className="control">
              <label htmlFor="veh-create-notes">Notes (optional)</label>
              <textarea
                id="veh-create-notes"
                value={form.notes}
                onChange={setField('notes')}
                maxLength={1000}
                rows={3}
              />
            </div>
            <p className="muted small-note">
              New records always start as <strong>SCHEDULED</strong> (set by the server).
            </p>
            <ErrorAlert message={createError} />
            <button type="submit" className="button-success" disabled={creating}>
              {creating ? 'Creating…' : 'Create maintenance record'}
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
          <label htmlFor="mnt-search">Search (vehicle, type, provider, ID)</label>
          <input
            id="mnt-search"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Type to filter…"
          />
        </div>
        <div className="control">
          <label htmlFor="mnt-filter">Status</label>
          <select id="mnt-filter" value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
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

      {loading && <p className="state-note">Loading maintenance records…</p>}

      {!loading && (
        <>
          <ErrorAlert message={error} />
          {backendDown && error && (
            <p className="muted">The backend server appears to be offline. Please try again shortly.</p>
          )}
          {error && <button type="button" onClick={load}>Try again</button>}
        </>
      )}

      {!loading && !error && records.length === 0 && (
        <div className="state-note">
          <p>No maintenance records found.</p>
        </div>
      )}

      {!loading && !error && records.length > 0 && filtered.length === 0 && (
        <div className="state-note">
          <p>No maintenance records match the current search or filter.</p>
        </div>
      )}

      {!loading && !error && filtered.length > 0 && (
        <>
          <p className="muted">
            {filtered.length} of {records.length} record{records.length === 1 ? '' : 's'}
          </p>
          {filtered.map((r) => (
            <article key={r.maintenanceId} className="card">
              <h3>
                Maintenance #{r.maintenanceId ?? '—'}{' '}
                <span className="badge">{r.status ?? '—'}</span>
              </h3>
              <p className="rate">
                {r.maintenanceType || 'Maintenance'} · ₹{r.cost ?? '—'}
              </p>
              <dl className="vehicle-facts">
                <div>
                  <dt>Vehicle</dt>
                  <dd>{r.vehicleBrand} {r.vehicleModel} ({r.vehicleRegistrationNumber ?? '—'})</dd>
                </div>
                <div><dt>Date</dt><dd>{fmt(r.maintenanceDate)}</dd></div>
                <div><dt>Completion</dt><dd>{fmt(r.completionDate)}</dd></div>
                <div><dt>Provider</dt><dd>{r.serviceProvider ?? '—'}</dd></div>
              </dl>
              <button type="button" onClick={() => openDetail(r.maintenanceId)}>View details</button>
            </article>
          ))}
        </>
      )}
    </section>
  )
}
