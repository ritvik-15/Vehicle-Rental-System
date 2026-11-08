import { useEffect, useState } from 'react'
import { api } from '../../services/api.js'
import ErrorAlert from '../../components/ErrorAlert.jsx'

// Phase 9 customer profile.
// Exact backend contracts (no invented fields):
//   GET   /api/users/me           -> UserProfileResponse (no credential fields)
//   PUT   /api/users/me           -> ProfileUpdateRequest { fullName, phone, address, city, state, pincode }
//   PATCH /api/users/me/password  -> PasswordChangeRequest { currentPassword, newPassword } -> { message }
// Email, role, status and userId are read-only here by backend contract.
const FIELD_LIMITS = { fullName: 100, phone: 20, address: 255, city: 100, state: 100, pincode: 10 }
const EMPTY_FORM = { fullName: '', phone: '', address: '', city: '', state: '', pincode: '' }

export default function ProfilePage() {
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')
  const [backendDown, setBackendDown] = useState(false)
  const [profile, setProfile] = useState(null)

  const [form, setForm] = useState(EMPTY_FORM)
  const [saving, setSaving] = useState(false)
  const [saveError, setSaveError] = useState('')
  const [saveOk, setSaveOk] = useState('')

  const [pw, setPw] = useState({ currentPassword: '', newPassword: '', confirm: '' })
  const [changingPw, setChangingPw] = useState(false)
  const [pwError, setPwError] = useState('')
  const [pwOk, setPwOk] = useState('')

  const applyProfile = (data) => {
    setProfile(data)
    setForm({
      fullName: data.fullName ?? '',
      phone: data.phone ?? '',
      address: data.address ?? '',
      city: data.city ?? '',
      state: data.state ?? '',
      pincode: data.pincode ?? '',
    })
  }

  const load = () => {
    setLoading(true)
    setLoadError('')
    setBackendDown(false)
    api.get('/api/users/me')
      .then((data) => applyProfile(data))
      .catch((err) => {
        setProfile(null)
        setBackendDown(err?.status === 0)
        setLoadError(err?.message || 'Failed to load your profile.')
      })
      .finally(() => setLoading(false))
  }

  useEffect(() => { load() }, [])

  const setField = (key) => (e) => {
    setForm({ ...form, [key]: e.target.value })
    setSaveOk('')
    setSaveError('')
  }

  const onSaveProfile = async (e) => {
    e.preventDefault()
    if (saving) return
    const fullName = form.fullName.trim()
    const phone = form.phone.trim()
    if (!fullName) { setSaveError('Full name is required.'); return }
    if (!phone) { setSaveError('Phone is required.'); return }
    if (!/^[0-9]{10}$/.test(phone)) { setSaveError('Phone number must contain exactly 10 digits.'); return }
    if (fullName.length > FIELD_LIMITS.fullName) { setSaveError('Full name must not exceed 100 characters.'); return }
    if (phone.length > FIELD_LIMITS.phone) { setSaveError('Phone must not exceed 20 characters.'); return }
    if (form.address.length > FIELD_LIMITS.address || form.city.length > FIELD_LIMITS.city ||
        form.state.length > FIELD_LIMITS.state || form.pincode.length > FIELD_LIMITS.pincode) {
      setSaveError('One or more address fields exceed their maximum length.'); return
    }
    setSaving(true)
    setSaveError('')
    setSaveOk('')
    try {
      const data = await api.put('/api/users/me', {
        fullName,
        phone,
        address: form.address.trim() || null,
        city: form.city.trim() || null,
        state: form.state.trim() || null,
        pincode: form.pincode.trim() || null,
      })
      applyProfile(data)
      setSaveOk('Profile updated successfully.')
    } catch (err) {
      setSaveError(err?.message || 'Failed to update your profile.')
    } finally {
      setSaving(false)
    }
  }

  const setPwField = (key) => (e) => {
    setPw({ ...pw, [key]: e.target.value })
    setPwOk('')
    setPwError('')
  }

  const onChangePassword = async (e) => {
    e.preventDefault()
    if (changingPw) return
    if (!pw.currentPassword) { setPwError('Current password is required.'); return }
    if (pw.newPassword.length < 6) { setPwError('New password must be at least 6 characters.'); return }
    if (pw.newPassword !== pw.confirm) { setPwError('New password and confirmation do not match.'); return }
    setChangingPw(true)
    setPwError('')
    setPwOk('')
    try {
      const data = await api.patch('/api/users/me/password', {
        currentPassword: pw.currentPassword,
        newPassword: pw.newPassword,
      })
      setPw({ currentPassword: '', newPassword: '', confirm: '' })
      setPwOk(data?.message || 'Password updated successfully.')
    } catch (err) {
      setPwError(err?.message || 'Failed to change your password.')
    } finally {
      setChangingPw(false)
    }
  }

  if (loading) return <p className="state-note">Loading profile…</p>

  if (loadError) {
    return (
      <section>
        <h1>Profile</h1>
        <ErrorAlert message={loadError} />
        {backendDown && <p className="muted">The backend server appears to be offline. Please try again shortly.</p>}
        <button type="button" onClick={load}>Try again</button>
      </section>
    )
  }

  return (
    <section>
      <h1>Profile</h1>

      <div className="card">
        <h2>Account</h2>
        <dl className="confirmation-details">
          <div><dt>Email</dt><dd>{profile.email}</dd></div>
          <div><dt>Role</dt><dd>{profile.role}</dd></div>
          <div><dt>Status</dt><dd>{profile.status}</dd></div>
          <div><dt>Member since</dt><dd>{String(profile.createdAt || '').replace('T', ' ').slice(0, 16)}</dd></div>
        </dl>
        <p className="muted small-note">Email and role are managed by the system and cannot be edited here.</p>
      </div>

      <div className="card">
        <h2>Edit profile</h2>
        <form onSubmit={onSaveProfile} noValidate>
          <div className="control">
            <label htmlFor="profile-name">Full name</label>
            <input id="profile-name" value={form.fullName} onChange={setField('fullName')} maxLength={FIELD_LIMITS.fullName} required />
          </div>
          <div className="control">
            <label htmlFor="profile-phone">Phone</label>
            <input id="profile-phone" value={form.phone} onChange={setField('phone')} maxLength={FIELD_LIMITS.phone} required />
          </div>
          <div className="control">
            <label htmlFor="profile-address">Address</label>
            <input id="profile-address" value={form.address} onChange={setField('address')} maxLength={FIELD_LIMITS.address} />
          </div>
          <div className="control">
            <label htmlFor="profile-city">City</label>
            <input id="profile-city" value={form.city} onChange={setField('city')} maxLength={FIELD_LIMITS.city} />
          </div>
          <div className="control">
            <label htmlFor="profile-state">State</label>
            <input id="profile-state" value={form.state} onChange={setField('state')} maxLength={FIELD_LIMITS.state} />
          </div>
          <div className="control">
            <label htmlFor="profile-pincode">Pincode</label>
            <input id="profile-pincode" value={form.pincode} onChange={setField('pincode')} maxLength={FIELD_LIMITS.pincode} />
          </div>
          <ErrorAlert message={saveError} />
          {saveOk && <p className="success-alert" role="status">{saveOk}</p>}
          <button type="submit" className="button-primary" disabled={saving}>
            {saving ? 'Saving…' : 'Save changes'}
          </button>
        </form>
      </div>

      <div className="card">
        <h2>Change password</h2>
        <form onSubmit={onChangePassword} noValidate>
          <div className="control">
            <label htmlFor="pw-current">Current password</label>
            <input id="pw-current" type="password" autoComplete="current-password" value={pw.currentPassword} onChange={setPwField('currentPassword')} required />
          </div>
          <div className="control">
            <label htmlFor="pw-new">New password</label>
            <input id="pw-new" type="password" autoComplete="new-password" value={pw.newPassword} onChange={setPwField('newPassword')} minLength={6} required />
          </div>
          <div className="control">
            <label htmlFor="pw-confirm">Confirm new password</label>
            <input id="pw-confirm" type="password" autoComplete="new-password" value={pw.confirm} onChange={setPwField('confirm')} minLength={6} required />
          </div>
          <ErrorAlert message={pwError} />
          {pwOk && <p className="success-alert" role="status">{pwOk}</p>}
          <button type="submit" className="button-primary" disabled={changingPw}>
            {changingPw ? 'Updating…' : 'Change password'}
          </button>
          <p className="muted small-note">Minimum 6 characters. Your password is never shown on this page.</p>
        </form>
      </div>
    </section>
  )
}
