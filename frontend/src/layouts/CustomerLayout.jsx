import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext.jsx'

// Minimal customer shell: name, navigation, logout, content area.
// Guests see the same shell with public nav (browse + login/register);
// customer-only links appear only for an authenticated CUSTOMER session.
export default function CustomerLayout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const isCustomer = user?.role === 'CUSTOMER'

  const onLogout = () => {
    logout()
    navigate('/login', { replace: true })
  }

  return (
    <div className="shell">
      <header className="topbar customer">
        <span className="brand">Vehicle Rental</span>
        <nav>
          <NavLink to="/home">Home</NavLink>
          {isCustomer && <NavLink to="/my-rentals">My Rentals</NavLink>}
          {isCustomer && <NavLink to="/profile">Profile</NavLink>}
        </nav>
        <div className="topbar-end">
          {user ? (
            <>
              <span className="muted">{user?.fullName}</span>
              <button type="button" onClick={onLogout}>Logout</button>
            </>
          ) : (
            <>
              <NavLink to="/login">Login</NavLink>
              <NavLink to="/register">Register</NavLink>
            </>
          )}
        </div>
      </header>
      <main className="content">
        <Outlet />
      </main>
    </div>
  )
}
