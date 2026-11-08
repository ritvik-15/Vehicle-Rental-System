// Small native-fetch API client. No business logic lives here — only transport:
// base URL, Bearer token, JSON handling and common HTTP error handling.
const BASE = String(import.meta.env?.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/+$/, '')
const SESSION_KEY = 'vr_session'

export class ApiError extends Error {
  constructor(status, message, body) {
    super(message)
    this.status = status
    this.body = body ?? null
  }
}

// --- session persistence (stateless JWT: token + profile only, never passwords) ---
export function getSession() {
  try {
    const raw = sessionStorage.getItem(SESSION_KEY)
    if (!raw) return null
    const session = JSON.parse(raw)
    if (!session || typeof session.token !== 'string' || !session.user) {
      sessionStorage.removeItem(SESSION_KEY)
      return null
    }
    return session
  } catch {
    try { sessionStorage.removeItem(SESSION_KEY) } catch { /* storage unavailable */ }
    return null
  }
}

export function saveSession(session) {
  sessionStorage.setItem(SESSION_KEY, JSON.stringify(session))
}

export function clearSession() {
  try { sessionStorage.removeItem(SESSION_KEY) } catch { /* storage unavailable */ }
}

async function request(path, { method = 'GET', body, auth = true } = {}) {
  const headers = {}
  const session = auth ? getSession() : null
  if (session?.token) headers['Authorization'] = `Bearer ${session.token}`
  if (body !== undefined) headers['Content-Type'] = 'application/json'

  let res
  try {
    res = await fetch(BASE + path, {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
    })
  } catch {
    throw new ApiError(0, 'Cannot reach the server. Please check that the backend is running.')
  }

  let data = null
  if (res.status !== 204) {
    const text = await res.text()
    if (text) {
      try { data = JSON.parse(text) } catch { data = null }
    }
  }

  if (!res.ok) {
    let message = (data && (data.error || data.message)) || `Request failed (HTTP ${res.status}).`
    if (data && Array.isArray(data.details) && data.details.length) {
      message = `${message}: ${data.details.join('; ')}`
    }
    // Only a 401 on a token-authenticated request invalidates the session.
    if (res.status === 401 && session?.token) {
      clearSession()
      if (typeof window !== 'undefined') {
        window.dispatchEvent(new Event('vr:session-expired'))
      }
    }
    throw new ApiError(res.status, message, data)
  }
  return data
}

export const api = {
  get: (path, opts) => request(path, { ...opts, method: 'GET' }),
  post: (path, body, opts) => request(path, { ...opts, method: 'POST', body }),
  put: (path, body, opts) => request(path, { ...opts, method: 'PUT', body }),
  patch: (path, body, opts) => request(path, { ...opts, method: 'PATCH', body }),
  del: (path, opts) => request(path, { ...opts, method: 'DELETE' }),
}
