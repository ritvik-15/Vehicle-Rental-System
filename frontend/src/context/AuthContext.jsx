import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { api, ApiError, clearSession, getSession, saveSession } from '../services/api.js'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  // Restore the session after a page refresh (sessionStorage; stateless JWT).
  const [session, setSession] = useState(() => getSession())

  // A 401 on an authenticated request clears the session in the API client;
  // this listener propagates that into React state so guards can react.
  useEffect(() => {
    const onExpired = () => setSession(null)
    window.addEventListener('vr:session-expired', onExpired)
    return () => window.removeEventListener('vr:session-expired', onExpired)
  }, [])

  // Sync sessionStorage changes from other tabs or external writes
  // (e.g., OAuth redirects). The API client reads sessionStorage directly
  // on each request, but React state must stay in sync for route guards.
  useEffect(() => {
    const onStorage = (e) => {
      if (e.key === 'vr_session') {
        try {
          const session = e.newValue ? JSON.parse(e.newValue) : null
          setSession(session)
        } catch {
          setSession(null)
        }
      }
    }
    window.addEventListener('storage', onStorage)
    return () => window.removeEventListener('storage', onStorage)
  }, [])

  // Role comes from the approved login contract — never from /api/users/me
  // (that endpoint is CUSTOMER-only and must not be called for ADMIN users).
  const login = useCallback(async (email, password) => {
    const data = await api.post('/api/auth/login', { email, password }, { auth: false })
    if (!data || typeof data.token !== 'string' || !data.role) {
      throw new ApiError(0, 'Unexpected response from the server.')
    }
    const next = {
      token: data.token,
      user: {
        userId: data.userId,
        fullName: data.fullName,
        email: data.email,
        role: data.role,
      },
    }
    saveSession(next)
    setSession(next)
    return next
  }, [])

  // Registration returns no token; after success the user signs in manually.
  const register = useCallback(
    (payload) => api.post('/api/auth/register', payload, { auth: false }),
    [],
  )

  // Client-side logout only — the approved backend is stateless JWT (no logout endpoint).
  const logout = useCallback(() => {
    clearSession()
    setSession(null)
  }, [])

  const value = useMemo(
    () => ({
      user: session?.user ?? null,
      isAuthenticated: Boolean(session?.user),
      login,
      register,
      logout,
    }),
    [session, login, register, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  return useContext(AuthContext)
}
