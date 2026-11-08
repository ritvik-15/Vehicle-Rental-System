import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../context/AuthContext.jsx'

// Requires an authenticated session; otherwise redirects to /login.
export function RequireAuth() {
  const { isAuthenticated } = useAuth()
  const location = useLocation()
  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location }} />
  }
  return <Outlet />
}

// Requires a specific role. Used nested inside RequireAuth, so the user is
// always authenticated here — a mismatch is an access-denied case, not a
// login case. Frontend guards are UX only; the backend enforces authorization.
export function RequireRole({ role }) {
  const { user } = useAuth()
  if (user?.role !== role) {
    return <Navigate to="/access-denied" replace />
  }
  return <Outlet />
}
