import { Link } from 'react-router-dom'
import { useAuth } from '../context/AuthContext.jsx'
import { homeFor } from '../utils/paths.js'

// Shown when an authenticated user requests a route their role does not allow.
export default function AccessDeniedPage() {
  const { user } = useAuth()
  return (
    <div className="shell">
      <header className="topbar">
        <span className="brand">Vehicle Rental</span>
      </header>
      <main className="content">
        <section className="card auth-card">
          <h1>Access denied</h1>
          <p className="muted">
            Your account does not have permission to view that area.
          </p>
          <p>
            <Link className="button" to={user ? homeFor(user.role) : '/login'}>
              {user ? 'Back to my area' : 'Go to login'}
            </Link>
          </p>
        </section>
      </main>
    </div>
  )
}
