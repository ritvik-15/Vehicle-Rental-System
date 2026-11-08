import { Navigate, Routes, Route } from 'react-router-dom'
import { RequireAuth, RequireRole } from './components/RouteGuards.jsx'
import CustomerLayout from './layouts/CustomerLayout.jsx'
import AdminLayout from './layouts/AdminLayout.jsx'
import LoginPage from './pages/LoginPage.jsx'
import RegisterPage from './pages/RegisterPage.jsx'
import AccessDeniedPage from './pages/AccessDeniedPage.jsx'
import NotFoundPage from './pages/NotFoundPage.jsx'
import HomePage from './pages/customer/HomePage.jsx'
import VehicleDetailsPage from './pages/customer/VehicleDetailsPage.jsx'
import BookingPage from './pages/customer/BookingPage.jsx'
import ProfilePage from './pages/customer/ProfilePage.jsx'
import MyRentalsPage from './pages/customer/MyRentalsPage.jsx'
import AdminDashboard from './pages/admin/AdminDashboard.jsx'
import VehiclesPage from './pages/admin/VehiclesPage.jsx'
import CustomersPage from './pages/admin/CustomersPage.jsx'
import RentalsPage from './pages/admin/RentalsPage.jsx'
import PaymentsPage from './pages/admin/PaymentsPage.jsx'
import MaintenancePage from './pages/admin/MaintenancePage.jsx'
import PromoCodesPage from './pages/admin/PromoCodesPage.jsx'
import ReportsPage from './pages/admin/ReportsPage.jsx'

export default function App() {
  return (
    <Routes>
      {/* Public: root redirects to the vehicle browsing page */}
      <Route path="/" element={<Navigate to="/home" replace />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/access-denied" element={<AccessDeniedPage />} />

      {/* Public browsing: guests may list/search vehicles and view details
          (the backend already permits GET /api/vehicles/** without auth).
          Booking, profile and rentals remain guarded below. */}
      <Route element={<CustomerLayout />}>
        <Route path="/home" element={<HomePage />} />
        <Route path="/home/vehicles/:id" element={<VehicleDetailsPage />} />
      </Route>

      {/* Protected: authenticated session required */}
      <Route element={<RequireAuth />}>
        {/* CUSTOMER area */}
        <Route element={<RequireRole role="CUSTOMER" />}>
          <Route element={<CustomerLayout />}>
            <Route path="/home/vehicles/:id/book" element={<BookingPage />} />
            <Route path="/profile" element={<ProfilePage />} />
            <Route path="/my-rentals" element={<MyRentalsPage />} />
          </Route>
        </Route>

        {/* ADMIN area */}
        <Route element={<RequireRole role="ADMIN" />}>
          <Route element={<AdminLayout />}>
            <Route path="/admin" element={<AdminDashboard />} />
            <Route path="/admin/vehicles" element={<VehiclesPage />} />
            <Route path="/admin/customers" element={<CustomersPage />} />
            <Route path="/admin/rentals" element={<RentalsPage />} />
            <Route path="/admin/payments" element={<PaymentsPage />} />
            <Route path="/admin/maintenance" element={<MaintenancePage />} />
            <Route path="/admin/promos" element={<PromoCodesPage />} />
            <Route path="/admin/reports" element={<ReportsPage />} />
          </Route>
        </Route>
      </Route>

      {/* Not found */}
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  )
}
