import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext.jsx'

// Minimal admin shell: admin label, admin navigation, logout, content area.
export default function AdminLayout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  const onLogout = () => {
    logout()
    navigate('/login', { replace: true })
  }

  return (
    <div className="shell">
      <header className="topbar admin">
        <span className="brand">Vehicle Rental — Admin</span>
        <nav>
          <NavLink to="/admin" end>Dashboard</NavLink>
          <NavLink to="/admin/vehicles">Vehicles</NavLink>
          <NavLink to="/admin/customers">Customers</NavLink>
          <NavLink to="/admin/rentals">Rentals</NavLink>
          <NavLink to="/admin/payments">Payments</NavLink>
          <NavLink to="/admin/maintenance">Maintenance</NavLink>
          <NavLink to="/admin/promos">Promo Codes</NavLink>
          <NavLink to="/admin/reports">Reports</NavLink>
        </nav>
        <div className="topbar-end">
          <span className="muted">{user?.fullName}</span>
          <button type="button" onClick={onLogout}>Logout</button>
        </div>
      </header>
      <main className="content">
        <Outlet />
      </main>
    </div>
  )
}
