import { useRef, useState } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext.jsx'
import ErrorAlert from '../components/ErrorAlert.jsx'
import { homeFor, safeTarget } from '../utils/paths.js'

// POST /api/auth/login { email, password } -> { token, type, userId, fullName, email, role }
export default function LoginPage() {
  const { user, login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [pending, setPending] = useState(false)
  const pendingRef = useRef(false) // synchronous guard against repeated clicks

  if (user) return <Navigate to={homeFor(user.role)} replace />

  const onSubmit = async (event) => {
    event.preventDefault()
    if (pendingRef.current) return // ignore repeated login clicks
    pendingRef.current = true
    setError('')
    setPending(true)
    try {
      const session = await login(email.trim(), password)
      // Restore the exact intended destination (path + query, e.g. the booking
      // page with pickup/return dates) when safeTarget kept the deep link;
      // otherwise the role home is used with no query attached.
      const from = location.state?.from
      const target = safeTarget(session.user.role, from?.pathname)
      const restored = from && target === from.pathname
        ? `${target}${from.search || ''}`
        : target
      navigate(restored, { replace: true })
    } catch (err) {
      setError(err?.message || 'Login failed. Please try again.')
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
          <Link to="/register" state={{ from: location.state?.from }}>Register</Link>
        </nav>
      </header>
      <main className="content">
        <section className="card auth-card">
          <h1>Login</h1>
          <ErrorAlert message={error} />
          <form onSubmit={onSubmit} noValidate>
            <label htmlFor="login-email">Email</label>
            <input
              id="login-email"
              type="email"
              autoComplete="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
            />
            <label htmlFor="login-password">Password</label>
            <input
              id="login-password"
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
            <button type="submit" disabled={pending}>
              {pending ? 'Signing in…' : 'Login'}
            </button>
          </form>
          <p className="muted">
            No account? <Link to="/register" state={{ from: location.state?.from }}>Register</Link>
          </p>
        </section>
      </main>
    </div>
  )
}
