import { useEffect, useRef, useState } from 'react'
import { api } from '../../services/api.js'
import ErrorAlert from '../../components/ErrorAlert.jsx'
import VehicleImage from '../../components/VehicleImage.jsx'

// Phase 10B admin vehicle management (Steps 1–5: list, details, create,
// edit, status change, image management).
// Exact backend contracts:
//   GET  /api/admin/vehicles     -> List<VehicleResponse> (all statuses, no images)
//   GET  /api/admin/vehicles/{id} -> VehicleResponse (includes images)
//   POST /api/admin/vehicles     -> 201 VehicleResponse (409 duplicate registration, 400 validation)
//   PUT  /api/admin/vehicles/{id} -> 200 VehicleResponse (404 missing, 409 duplicate registration, 400 validation)
//   PATCH /api/admin/vehicles/{id}/status -> 200 VehicleResponse (404 missing, 400 validation/restricted value)
//   POST  /api/admin/vehicles/{id}/images -> 201 VehicleImageResponse {imageId, imageUrl, isPrimary, createdAt}
//   DELETE /api/admin/vehicles/{id}/images/{imageId} -> 204 (404 image not found for this vehicle)
// PUT accepts exactly the VehicleUpdateRequest fields (same set/validation as
// create); status, images and timestamps are immutable through this endpoint.
// PATCH body is VehicleStatusRequest { status }; only AVAILABLE and INACTIVE
// can be set manually — RENTED/MAINTENANCE are workflow-derived (400).
// List items never carry images, so images are only rendered after the
// detail request succeeds. Stale detail responses are ignored via seqRef.
const fmt = (value) => (value ? String(value).replace('T', ' ') : '—')

// Enum values verified against backend VehicleType/FuelType/TransmissionType.
const VEHICLE_TYPES = ['CAR', 'BIKE', 'BUS', 'TRUCK']
const FUEL_TYPES = ['PETROL', 'DIESEL', 'ELECTRIC', 'CNG', 'HYBRID']
const TRANSMISSIONS = ['MANUAL', 'AUTOMATIC', 'NOT_APPLICABLE']

// The only statuses the backend accepts from a manual PATCH …/status request
// (VehicleService.updateVehicleStatus). RENTED and MAINTENANCE are derived by
// the rental/maintenance workflows and return 400 if set manually.
const MANUAL_STATUS_TARGETS = ['AVAILABLE', 'INACTIVE']

const EMPTY_FORM = {
  registrationNumber: '', brand: '', model: '', vehicleType: '',
  description: '', dailyRate: '', seatingCapacity: '',
  fuelType: '', transmission: '', location: '',
}

// Frontend mirror of VehicleCreateRequest validation (backend stays authoritative).
function validateVehicleForm(f) {
  const reg = f.registrationNumber.trim()
  const brand = f.brand.trim()
  const model = f.model.trim()
  const location = f.location.trim()
  if (!reg) return 'Registration number is required.'
  if (reg.length > 30) return 'Registration number must not exceed 30 characters.'
  if (!brand) return 'Brand is required.'
  if (brand.length > 80) return 'Brand must not exceed 80 characters.'
  if (!model) return 'Model is required.'
  if (model.length > 100) return 'Model must not exceed 100 characters.'
  if (!VEHICLE_TYPES.includes(f.vehicleType)) return 'Vehicle type is required.'
  const rate = Number(f.dailyRate)
  if (f.dailyRate.trim() === '' || !Number.isFinite(rate)) return 'Daily rate must be a valid number.'
  if (rate < 0) return 'Daily rate must be 0 or greater.'
  const seats = Number(f.seatingCapacity)
  if (f.seatingCapacity.trim() === '' || !Number.isInteger(seats)) return 'Seating capacity must be a whole number.'
  if (seats < 1) return 'Seating capacity must be at least 1.'
  if (!FUEL_TYPES.includes(f.fuelType)) return 'Fuel type is required.'
  if (!TRANSMISSIONS.includes(f.transmission)) return 'Transmission is required.'
  if (!location) return 'Location is required.'
  if (location.length > 150) return 'Location must not exceed 150 characters.'
  if (f.description.length > 1000) return 'Description must not exceed 1000 characters.'
  return null
}

export default function VehiclesPage() {
  const [vehicles, setVehicles] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [backendDown, setBackendDown] = useState(false)

  const [selectedId, setSelectedId] = useState(null)
  const [detail, setDetail] = useState(null)
  const [detailLoading, setDetailLoading] = useState(false)
  const [detailError, setDetailError] = useState('')
  const [detailDown, setDetailDown] = useState(false)
  const seqRef = useRef(0) // ignore out-of-order detail responses

  const [showForm, setShowForm] = useState(false)
  const [form, setForm] = useState(EMPTY_FORM)
  const [creating, setCreating] = useState(false)
  const [createError, setCreateError] = useState('')
  const [createOk, setCreateOk] = useState('')

  const [editForm, setEditForm] = useState(null) // prefilled form while editing; null = detail view
  const [updating, setUpdating] = useState(false)
  const [updateError, setUpdateError] = useState('')
  const [updateOk, setUpdateOk] = useState('')

  const [statusChoice, setStatusChoice] = useState('')
  const [statusUpdating, setStatusUpdating] = useState(false)
  const [statusError, setStatusError] = useState('')
  const [statusOk, setStatusOk] = useState('')

  // Image management state (kept separate from create/edit/status forms).
  // Backend rules verified in VehicleService: isPrimary is optional; adding
  // with isPrimary=true demotes existing primaries; duplicate URLs are
  // allowed; primary and final images may be deleted; no replacement primary
  // is chosen automatically.
  const [imgUrl, setImgUrl] = useState('')
  const [imgPrimary, setImgPrimary] = useState(false)
  const [addingImage, setAddingImage] = useState(false)
  const [imageError, setImageError] = useState('')
  const [imageOk, setImageOk] = useState('')
  const [deletingImageId, setDeletingImageId] = useState(null)

  const load = () => {
    setLoading(true)
    setError('')
    setBackendDown(false)
    api.get('/api/admin/vehicles')
      .then((data) => setVehicles(Array.isArray(data) ? data : []))
      .catch((err) => {
        setVehicles([])
        setBackendDown(err?.status === 0)
        setError(err?.message || 'Failed to load vehicles.')
      })
      .finally(() => setLoading(false))
  }

  useEffect(() => { load() }, [])

  // Fetches the selected vehicle's detail (seq-guarded). Returns a promise
  // resolving to true when the refresh applied, false on failure/stale — used
  // by the image handlers to distinguish "operation succeeded but refresh
  // failed" from an operation failure.
  const openDetail = (vehicleId) => {
    const seq = ++seqRef.current
    setSelectedId(vehicleId)
    setDetail(null)
    setDetailError('')
    setDetailDown(false)
    setDetailLoading(true)
    return api.get(`/api/admin/vehicles/${vehicleId}`)
      .then((data) => {
        if (seq === seqRef.current) setDetail(data)
        return seq === seqRef.current
      })
      .catch((err) => {
        if (seq !== seqRef.current) return false
        setDetailDown(err?.status === 0)
        setDetailError(err?.message || 'Failed to load vehicle details.')
        return false
      })
      .finally(() => {
        if (seq === seqRef.current) setDetailLoading(false)
      })
  }

  const closeDetail = () => {
    seqRef.current += 1 // invalidate any in-flight detail request
    setSelectedId(null)
    setDetail(null)
    setDetailError('')
    setDetailDown(false)
    setEditForm(null) // never carry an edit form across selections
    setUpdateError('')
    setUpdateOk('')
    setStatusChoice('') // never carry a pending status choice across selections
    setStatusError('')
    setStatusOk('')
    setImgUrl('') // never carry image form state across selections
    setImgPrimary(false)
    setImageError('')
    setImageOk('')
    setDeletingImageId(null)
  }

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

  const onSubmitVehicle = async (e) => {
    e.preventDefault()
    if (creating) return // duplicate-submission guard (submit button is also disabled)
    const invalid = validateVehicleForm(form)
    if (invalid) {
      setCreateError(invalid)
      return
    }
    setCreating(true)
    setCreateError('')
    setCreateOk('')
    try {
      const created = await api.post('/api/admin/vehicles', {
        registrationNumber: form.registrationNumber.trim(),
        brand: form.brand.trim(),
        model: form.model.trim(),
        vehicleType: form.vehicleType,
        description: form.description.trim() || null,
        dailyRate: Number(form.dailyRate),
        seatingCapacity: Number(form.seatingCapacity),
        fuelType: form.fuelType,
        transmission: form.transmission,
        location: form.location.trim(),
      })
      // Only after the server confirms success do we close and reset the form.
      setShowForm(false)
      setForm(EMPTY_FORM)
      setCreateOk(
        created?.registrationNumber
          ? `Vehicle ${created.registrationNumber} created successfully.`
          : 'Vehicle created successfully.',
      )
      // Refresh the list. A refresh failure surfaces separately in the list
      // error state — its "Try again" reloads the list only, and the success
      // message stays visible so creation is never implied to have failed.
      load()
    } catch (err) {
      // Entered values are preserved so the admin can correct and resubmit.
      setCreateError(err?.message || 'Failed to create the vehicle.')
    } finally {
      setCreating(false)
    }
  }

  const openEdit = () => {
    if (!detail || updating) return
    setUpdateError('')
    setUpdateOk('')
    // Prefill every editable field from the loaded detail so unchanged
    // required values are always submitted back to the backend.
    setEditForm({
      registrationNumber: detail.registrationNumber ?? '',
      brand: detail.brand ?? '',
      model: detail.model ?? '',
      vehicleType: detail.vehicleType ?? '',
      description: detail.description ?? '',
      dailyRate: detail.dailyRate != null ? String(detail.dailyRate) : '',
      seatingCapacity: detail.seatingCapacity != null ? String(detail.seatingCapacity) : '',
      fuelType: detail.fuelType ?? '',
      transmission: detail.transmission ?? '',
      location: detail.location ?? '',
    })
  }

  const setEditField = (key) => (e) => {
    setEditForm((f) => (f ? { ...f, [key]: e.target.value } : f))
    setUpdateError('')
  }

  const cancelEdit = () => {
    if (updating) return // no cancelling while a submission is in flight
    setEditForm(null)
    setUpdateError('')
  }

  const onSubmitUpdate = async (e) => {
    e.preventDefault()
    if (updating || !editForm) return // duplicate-submission guard (button also disabled)
    const invalid = validateVehicleForm(editForm) // same constraints as create
    if (invalid) {
      setUpdateError(invalid)
      return
    }
    setUpdating(true)
    setUpdateError('')
    setUpdateOk('')
    try {
      const updated = await api.put(`/api/admin/vehicles/${selectedId}`, {
        registrationNumber: editForm.registrationNumber.trim(),
        brand: editForm.brand.trim(),
        model: editForm.model.trim(),
        vehicleType: editForm.vehicleType,
        description: editForm.description.trim() || null,
        dailyRate: Number(editForm.dailyRate),
        seatingCapacity: Number(editForm.seatingCapacity),
        fuelType: editForm.fuelType,
        transmission: editForm.transmission,
        location: editForm.location.trim(),
      })
      // Confirmed success: close the form, keep the selection, then refresh.
      setEditForm(null)
      setUpdateOk(
        updated?.registrationNumber
          ? `Vehicle ${updated.registrationNumber} updated successfully.`
          : 'Vehicle updated successfully.',
      )
      // Refresh list and detail with the existing fetch logic. Any refresh
      // failure surfaces in the list/detail error states whose retry only
      // re-runs GETs — the success message stays visible, so the update is
      // never reported as failed and resubmission is not encouraged.
      load()
      openDetail(selectedId)
    } catch (err) {
      // Keep the form open with the entered values for correction and retry.
      setUpdateError(err?.message || 'Failed to update the vehicle.')
    } finally {
      setUpdating(false)
    }
  }

  const setStatusField = (e) => {
    setStatusChoice(e.target.value)
    setStatusError('')
  }

  const onSubmitStatus = async (e) => {
    e.preventDefault()
    if (statusUpdating || !detail) return // duplicate-submission guard (button also disabled)
    const target = (statusChoice || '').trim().toUpperCase()
    // Only the backend-permitted manual values are ever sent.
    if (!MANUAL_STATUS_TARGETS.includes(target)) {
      setStatusError('Select a status to apply.')
      return
    }
    // Never send a no-op change.
    if (target === detail.status) return
    setStatusUpdating(true)
    setStatusError('')
    setStatusOk('')
    try {
      const changed = await api.patch(`/api/admin/vehicles/${selectedId}/status`, { status: target })
      setStatusChoice('')
      setStatusOk(
        `Status of vehicle ${changed?.registrationNumber || selectedId} changed to ${changed?.status || target}.`,
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

  const onImageField = (e) => {
    setImgUrl(e.target.value)
    setImageError('')
  }

  const onAddImage = async (e) => {
    e.preventDefault()
    if (addingImage || !detail) return // duplicate-submission guard (button also disabled)
    // Trim ends only — a valid URL is never otherwise altered. The backend
    // does not trim and validates the raw value with @URL.
    const url = (imgUrl || '').trim()
    if (!url) {
      setImageError('Image URL is required.')
      return
    }
    if (url.length > 500) {
      setImageError('Image URL must not exceed 500 characters.')
      return
    }
    let parsed = null
    try {
      parsed = new URL(url)
    } catch {
      parsed = null
    }
    if (!parsed || (parsed.protocol !== 'http:' && parsed.protocol !== 'https:')) {
      setImageError('Image URL must be a valid http(s) URL.')
      return
    }
    setAddingImage(true)
    setImageError('')
    setImageOk('')
    try {
      const created = await api.post(`/api/admin/vehicles/${selectedId}/images`, {
        imageUrl: url,
        isPrimary: imgPrimary,
      })
      setImgUrl('')
      setImgPrimary(false)
      const label = created?.imageId != null ? `Image #${created.imageId}` : 'Image'
      const refreshed = await openDetail(selectedId)
      setImageOk(refreshed
        ? `${label} added successfully.`
        : `${label} was added successfully, but the vehicle details could not be refreshed. Use “Try again” below to reload — do not add it again.`)
    } catch (err) {
      // Keep the entered URL so the admin can correct and resubmit.
      setImageError(err?.message || 'Failed to add the image.')
    } finally {
      setAddingImage(false)
    }
  }

  const onDeleteImage = async (img) => {
    if (addingImage || deletingImageId != null) return // no concurrent image operations
    const imageId = img?.imageId
    if (imageId == null) return
    // Backend allows deleting any image (primary included, even the last
    // one) and picks no replacement primary — the gallery falls back to the
    // remaining images exactly as the backend returns them.
    const confirmed = window.confirm(
      `Delete image #${imageId}${img.isPrimary ? ' (primary)' : ''} from this vehicle? This cannot be undone.`,
    )
    if (!confirmed) return
    setDeletingImageId(imageId)
    setImageError('')
    setImageOk('')
    try {
      await api.del(`/api/admin/vehicles/${selectedId}/images/${imageId}`)
      const refreshed = await openDetail(selectedId)
      setImageOk(refreshed
        ? `Image #${imageId} deleted.`
        : `Image #${imageId} was deleted, but the vehicle details could not be refreshed. Use “Try again” below to reload.`)
    } catch (err) {
      setImageError(err?.message || 'Failed to delete the image.')
    } finally {
      setDeletingImageId(null)
    }
  }

  // ---- detail view (admin GET /{id}, images only come from here) ----
  if (selectedId !== null) {
    // Real image list from the detail response (createdAt ASC). The hero
    // image is the primary one (or the first), exactly like VehicleImage's
    // own selection; everything else is previewed below it.
    const imageList = Array.isArray(detail?.images) ? detail.images.filter((i) => i && i.imageUrl) : []
    const heroImage = imageList.find((i) => i.isPrimary) || imageList[0] || null
    const previewImages = imageList.filter((i) => i !== heroImage)
    return (
      <section>
        <p>
          <button type="button" className="button-secondary" onClick={closeDetail} disabled={updating || statusUpdating}>
            ← Back to vehicles
          </button>
        </p>

        {updateOk && <p className="success-alert" role="status">{updateOk}</p>}
        {statusOk && <p className="success-alert" role="status">{statusOk}</p>}
        {imageOk && <p className="success-alert" role="status">{imageOk}</p>}

        {detailLoading && <p className="state-note">Loading vehicle…</p>}

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
          <div className="detail-layout">
            <div className="detail-media">
              <VehicleImage
                images={detail.images}
                alt={`${detail.brand} ${detail.model}`}
                className="detail-main-image"
              />
              {imageList.length === 0 && (
                <p className="muted small-note">No images yet — add one by URL under Images.</p>
              )}
              {imageList.length > 1 && (
                <p className="muted">{imageList.length} photos</p>
              )}
              {previewImages.map((img) => (
                <div key={img.imageId ?? img.imageUrl}>
                  <VehicleImage
                    images={[img]}
                    alt={`${detail.brand} ${detail.model} image ${img.imageId ?? ''}`}
                    className="detail-main-image"
                  />
                  <p className="muted small-note">
                    Image #{img.imageId ?? '—'}{img.isPrimary ? ' · primary' : ''}
                  </p>
                </div>
              ))}
            </div>

            {editForm ? (
              <div className="detail-info card">
                <h2>Edit vehicle</h2>
                <p className="muted small-note">
                  Only editable details are shown. Status and images are managed separately.
                </p>
                <form onSubmit={onSubmitUpdate} noValidate>
                  <div className="control">
                    <label htmlFor="veh-edit-reg">Registration number</label>
                    <input id="veh-edit-reg" value={editForm.registrationNumber} onChange={setEditField('registrationNumber')} maxLength={30} required />
                  </div>
                  <div className="control">
                    <label htmlFor="veh-edit-brand">Brand</label>
                    <input id="veh-edit-brand" value={editForm.brand} onChange={setEditField('brand')} maxLength={80} required />
                  </div>
                  <div className="control">
                    <label htmlFor="veh-edit-model">Model</label>
                    <input id="veh-edit-model" value={editForm.model} onChange={setEditField('model')} maxLength={100} required />
                  </div>
                  <div className="control">
                    <label htmlFor="veh-edit-type">Vehicle type</label>
                    <select id="veh-edit-type" value={editForm.vehicleType} onChange={setEditField('vehicleType')}>
                      <option value="">Select type</option>
                      {VEHICLE_TYPES.map((t) => (
                        <option key={t} value={t}>{t}</option>
                      ))}
                    </select>
                  </div>
                  <div className="control">
                    <label htmlFor="veh-edit-rate">Daily rate (₹)</label>
                    <input id="veh-edit-rate" type="number" min="0" step="0.01" value={editForm.dailyRate} onChange={setEditField('dailyRate')} required />
                  </div>
                  <div className="control">
                    <label htmlFor="veh-edit-seats">Seating capacity</label>
                    <input id="veh-edit-seats" type="number" min="1" step="1" value={editForm.seatingCapacity} onChange={setEditField('seatingCapacity')} required />
                  </div>
                  <div className="control">
                    <label htmlFor="veh-edit-fuel">Fuel type</label>
                    <select id="veh-edit-fuel" value={editForm.fuelType} onChange={setEditField('fuelType')}>
                      <option value="">Select fuel</option>
                      {FUEL_TYPES.map((f) => (
                        <option key={f} value={f}>{f}</option>
                      ))}
                    </select>
                  </div>
                  <div className="control">
                    <label htmlFor="veh-edit-trans">Transmission</label>
                    <select id="veh-edit-trans" value={editForm.transmission} onChange={setEditField('transmission')}>
                      <option value="">Select transmission</option>
                      {TRANSMISSIONS.map((t) => (
                        <option key={t} value={t}>{t}</option>
                      ))}
                    </select>
                  </div>
                  <div className="control">
                    <label htmlFor="veh-edit-loc">Location</label>
                    <input id="veh-edit-loc" value={editForm.location} onChange={setEditField('location')} maxLength={150} required />
                  </div>
                  <div className="control">
                    <label htmlFor="veh-edit-desc">Description (optional)</label>
                    <input id="veh-edit-desc" value={editForm.description} onChange={setEditField('description')} maxLength={1000} />
                  </div>
                  <ErrorAlert message={updateError} />
                  <button type="submit" className="button-success" disabled={updating}>
                    {updating ? 'Saving…' : 'Save changes'}
                  </button>
                  <button type="button" className="button-secondary" onClick={cancelEdit} disabled={updating}>
                    Cancel
                  </button>
                </form>
              </div>
            ) : (
              <div className="detail-info card">
                <h1>{detail.brand} {detail.model}</h1>
                <p className="badges">
                  <span className="badge">{detail.vehicleType}</span>
                  <span className="badge">{detail.fuelType}</span>
                  <span className="badge">{detail.transmission}</span>
                  <span className={`badge badge-${(detail.status || '').toLowerCase()}`}>{detail.status}</span>
                </p>
                <p className="rate">₹{detail.dailyRate}<span>/day</span></p>
                {detail.description && <p>{detail.description}</p>}
                <dl className="vehicle-facts detail-facts">
                  <div><dt>Vehicle ID</dt><dd>{detail.vehicleId}</dd></div>
                  <div><dt>Registration</dt><dd>{detail.registrationNumber}</dd></div>
                  <div><dt>Type</dt><dd>{detail.vehicleType}</dd></div>
                  <div><dt>Seats</dt><dd>{detail.seatingCapacity}</dd></div>
                  <div><dt>Location</dt><dd>{detail.location ?? '—'}</dd></div>
                  <div><dt>Fuel</dt><dd>{detail.fuelType}</dd></div>
                  <div><dt>Transmission</dt><dd>{detail.transmission}</dd></div>
                  <div><dt>Status</dt><dd>{detail.status}</dd></div>
                  <div><dt>Created</dt><dd>{fmt(detail.createdAt)}</dd></div>
                  <div><dt>Last updated</dt><dd>{fmt(detail.updatedAt)}</dd></div>
                </dl>

                <h2>Status</h2>
                <p>
                  Current status:{' '}
                  <span className={`badge badge-${(detail.status || '').toLowerCase()}`}>{detail.status}</span>
                </p>
                {MANUAL_STATUS_TARGETS.includes(detail.status) ? (
                  <form onSubmit={onSubmitStatus} noValidate>
                    <div className="control">
                      <label htmlFor="veh-status">Set status to</label>
                      <select id="veh-status" value={statusChoice} onChange={setStatusField}>
                        <option value="">Select status</option>
                        {MANUAL_STATUS_TARGETS.filter((s) => s !== detail.status).map((s) => (
                          <option key={s} value={s}>{s}</option>
                        ))}
                      </select>
                    </div>
                    <ErrorAlert message={statusError} />
                    <button type="submit" className="button-success" disabled={statusUpdating || !statusChoice}>
                      {statusUpdating ? 'Updating…' : 'Change status'}
                    </button>
                  </form>
                ) : (
                  <p className="muted small-note">
                    A {detail.status} vehicle is managed by the rental and maintenance workflows;
                    its status cannot be changed manually here.
                  </p>
                )}

                <h2>Images</h2>
                {imageList.length === 0 && (
                  <p className="muted small-note">No images yet.</p>
                )}
                {imageList.map((img) => (
                  <p key={img.imageId ?? img.imageUrl} className="muted small-note">
                    Image #{img.imageId ?? '—'} ·{' '}
                    {img.isPrimary
                      ? <span className="badge">PRIMARY</span>
                      : 'secondary'}{' '}
                    · added {fmt(img.createdAt)}{' '}
                    <button
                      type="button"
                      className="button-secondary"
                      onClick={() => onDeleteImage(img)}
                      disabled={addingImage || deletingImageId != null}
                    >
                      {deletingImageId === img.imageId ? 'Deleting…' : 'Delete'}
                    </button>
                  </p>
                ))}
                <form onSubmit={onAddImage} noValidate>
                  <div className="control">
                    <label htmlFor="veh-img-url">Image URL</label>
                    <input
                      id="veh-img-url"
                      type="url"
                      value={imgUrl}
                      onChange={onImageField}
                      maxLength={500}
                      placeholder="https://…"
                      required
                    />
                  </div>
                  <p>
                    <label>
                      <input
                        type="checkbox"
                        checked={imgPrimary}
                        onChange={(e) => { setImgPrimary(e.target.checked); setImageError('') }}
                      />{' '}
                      Set as primary image
                    </label>
                  </p>
                  <p className="muted small-note">
                    Duplicate URLs are allowed. Checking primary unsets the current primary image
                    on the server; deleting an image (primary or not, including the last one) is
                    permanent.
                  </p>
                  <ErrorAlert message={imageError} />
                  <button type="submit" className="button-success" disabled={addingImage}>
                    {addingImage ? 'Adding…' : 'Add image'}
                  </button>
                </form>

                <p>
                  <button type="button" onClick={openEdit} disabled={statusUpdating}>Edit vehicle</button>
                </p>
              </div>
            )}
          </div>
        )}
      </section>
    )
  }

  // ---- list view (admin GET /, create via admin POST /) ----
  return (
    <section>
      <h1>Vehicles</h1>

      {createOk && <p className="success-alert" role="status">{createOk}</p>}

      <p>
        <button type="button" className="button-success" onClick={openForm} disabled={showForm}>
          Add vehicle
        </button>
      </p>

      {showForm && (
        <div className="card">
          <h2>Add vehicle</h2>
          <p className="muted small-note">
            All fields required unless marked optional. A new vehicle is created with the default
            AVAILABLE status; images are managed separately.
          </p>
          <form onSubmit={onSubmitVehicle} noValidate>
            <div className="control">
              <label htmlFor="veh-reg">Registration number</label>
              <input id="veh-reg" value={form.registrationNumber} onChange={setField('registrationNumber')} maxLength={30} required />
            </div>
            <div className="control">
              <label htmlFor="veh-brand">Brand</label>
              <input id="veh-brand" value={form.brand} onChange={setField('brand')} maxLength={80} required />
            </div>
            <div className="control">
              <label htmlFor="veh-model">Model</label>
              <input id="veh-model" value={form.model} onChange={setField('model')} maxLength={100} required />
            </div>
            <div className="control">
              <label htmlFor="veh-type">Vehicle type</label>
              <select id="veh-type" value={form.vehicleType} onChange={setField('vehicleType')}>
                <option value="">Select type</option>
                {VEHICLE_TYPES.map((t) => (
                  <option key={t} value={t}>{t}</option>
                ))}
              </select>
            </div>
            <div className="control">
              <label htmlFor="veh-rate">Daily rate (₹)</label>
              <input id="veh-rate" type="number" min="0" step="0.01" value={form.dailyRate} onChange={setField('dailyRate')} required />
            </div>
            <div className="control">
              <label htmlFor="veh-seats">Seating capacity</label>
              <input id="veh-seats" type="number" min="1" step="1" value={form.seatingCapacity} onChange={setField('seatingCapacity')} required />
            </div>
            <div className="control">
              <label htmlFor="veh-fuel">Fuel type</label>
              <select id="veh-fuel" value={form.fuelType} onChange={setField('fuelType')}>
                <option value="">Select fuel</option>
                {FUEL_TYPES.map((f) => (
                  <option key={f} value={f}>{f}</option>
                ))}
              </select>
            </div>
            <div className="control">
              <label htmlFor="veh-trans">Transmission</label>
              <select id="veh-trans" value={form.transmission} onChange={setField('transmission')}>
                <option value="">Select transmission</option>
                {TRANSMISSIONS.map((t) => (
                  <option key={t} value={t}>{t}</option>
                ))}
              </select>
            </div>
            <div className="control">
              <label htmlFor="veh-loc">Location</label>
              <input id="veh-loc" value={form.location} onChange={setField('location')} maxLength={150} required />
            </div>
            <div className="control">
              <label htmlFor="veh-desc">Description (optional)</label>
              <input id="veh-desc" value={form.description} onChange={setField('description')} maxLength={1000} />
            </div>
            <ErrorAlert message={createError} />
            <button type="submit" className="button-success" disabled={creating}>
              {creating ? 'Creating…' : 'Create vehicle'}
            </button>
            <button type="button" className="button-secondary" onClick={cancelForm} disabled={creating}>
              Cancel
            </button>
          </form>
        </div>
      )}

      {loading && <p className="state-note">Loading vehicles…</p>}

      {!loading && (
        <>
          <ErrorAlert message={error} />
          {backendDown && error && (
            <p className="muted">The backend server appears to be offline. Please try again shortly.</p>
          )}
          {error && <button type="button" onClick={load}>Try again</button>}
        </>
      )}

      {!loading && !error && vehicles.length === 0 && (
        <div className="state-note">
          <p>No vehicles found.</p>
        </div>
      )}

      {!loading && !error && vehicles.length > 0 && (
        <>
          <p className="muted">{vehicles.length} vehicle{vehicles.length === 1 ? '' : 's'}</p>
          <div className="vehicle-grid">
            {vehicles.map((v) => (
              <article key={v.vehicleId} className="card vehicle-card">
                {/* List responses have no images by contract — media is only shown in the detail view. */}
                <div className="vehicle-card-body">
                  <h3>{v.brand} {v.model}</h3>
                  <p className="badges">
                    <span className="badge">{v.vehicleType}</span>
                    <span className={`badge badge-${(v.status || '').toLowerCase()}`}>{v.status}</span>
                  </p>
                  <dl className="vehicle-facts">
                    <div><dt>Registration</dt><dd>{v.registrationNumber}</dd></div>
                    <div><dt>Location</dt><dd>{v.location ?? '—'}</dd></div>
                    <div><dt>Seats</dt><dd>{v.seatingCapacity}</dd></div>
                  </dl>
                  <p className="rate">₹{v.dailyRate}<span>/day</span></p>
                  <button type="button" onClick={() => openDetail(v.vehicleId)}>View details</button>
                </div>
              </article>
            ))}
          </div>
        </>
      )}
    </section>
  )
}
