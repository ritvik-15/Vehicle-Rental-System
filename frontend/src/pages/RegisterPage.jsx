import { useRef, useState } from 'react'
import { Link, Navigate, useLocation } from 'react-router-dom'
import { useAuth } from '../context/AuthContext.jsx'
import ErrorAlert from '../components/ErrorAlert.jsx'
import { homeFor } from '../utils/paths.js'

// POST /api/auth/register { fullName, email, phone, password }
// -> 200 { message, userId, email } (no token: user signs in afterwards)
// -> 400 { error, details } | 409 { error: "Email/Phone already exists" }
export default function RegisterPage() {
  const { user, register } = useAuth()
  const location = useLocation()
  const [form, setForm] = useState({ fullName: '', email: '', phone: '', password: '' })
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const [pending, setPending] = useState(false)
  const pendingRef = useRef(false) // synchronous guard against repeated clicks

  if (user) return <Navigate to={homeFor(user.role)} replace />

  const update = (key) => (event) => setForm({ ...form, [key]: event.target.value })

  // Client-side phone validation: exactly 10 digits
  const validatePhone = (phone) => {
    const digitsOnly = phone.trim()
    if (!digitsOnly) return 'Phone is required.'
    if (!/^[0-9]{10}$/.test(digitsOnly)) return 'Phone number must contain exactly 10 digits.'
    return ''
  }

  const onSubmit = async (event) => {
    event.preventDefault()
    if (pendingRef.current) return // ignore repeated submit clicks
    pendingRef.current = true
    setError('')
    setSuccess('')
    setPending(true)
    try {
      const phoneError = validatePhone(form.phone)
      if (phoneError) {
        setError(phoneError)
        return
      }
      const data = await register({
        fullName: form.fullName.trim(),
        email: form.email.trim(),
        phone: form.phone.trim(),
        password: form.password,
      })
      setSuccess(
        data?.message
          ? `${data.message}. You can now sign in with ${data.email}.`
          : 'Registration successful. You can now sign in.',
      )
      setForm({ fullName: '', email: '', phone: '', password: '' })
    } catch (err) {
      setError(err?.message || 'Registration failed. Please try again.')
    } finally {
      pendingRef.current = false
      setPending(false)
    }
  }

  return (
    <div className="shell">
      <header className="topbar">
        <span className="brand">Vehicle Rental</span>
        <nav>
          <Link to="/">Home</Link>
          <Link to="/login" state={{ from: location.state?.from }}>Login</Link>
        </nav>
      </header>
      <main className="content">
        <section className="card auth-card">
          <h1>Create your account</h1>
          <ErrorAlert message={error} />
          {success && (
            <p className="success-alert" role="status">
              {success} <Link to="/login" state={{ from: location.state?.from }}>Go to login</Link>
            </p>
          )}
          <form onSubmit={onSubmit} noValidate>
            <label htmlFor="reg-name">Full name</label>
            <input id="reg-name" value={form.fullName} onChange={update('fullName')} maxLength={100} required />

            <label htmlFor="reg-email">Email</label>
            <input id="reg-email" type="email" value={form.email} onChange={update('email')} maxLength={150} required />

            <label htmlFor="reg-phone">Phone</label>
            <input id="reg-phone" value={form.phone} onChange={update('phone')} maxLength={20} required />

            <label htmlFor="reg-password">Password</label>
            <input
              id="reg-password"
              type="password"
              autoComplete="new-password"
              value={form.password}
              onChange={update('password')}
              minLength={6}
              required
            />
            <button type="submit" disabled={pending}>
              {pending ? 'Creating account…' : 'Register'}
            </button>
          </form>
          <p className="muted">
            Already registered? <Link to="/login" state={{ from: location.state?.from }}>Login</Link>
          </p>
        </section>
      </main>
    </div>
  )
}
