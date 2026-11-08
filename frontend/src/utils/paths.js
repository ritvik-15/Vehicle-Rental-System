// Single source of truth for role-based landing paths.
export function homeFor(role) {
  return role === 'ADMIN' ? '/admin' : '/home'
}

// Deep-link restore after login, but only when the target fits the role;
// otherwise fall back to the role's home (e.g. ADMIN never lands on /home).
// The target must be a same-app absolute path — never a protocol-relative
// (//host) or backslash trick that could leave the app (open redirect).
export function safeTarget(role, from) {
  const home = homeFor(role)
  if (!from || from === '/login' || from === '/register') return home
  if (typeof from !== 'string') return home
  if (!from.startsWith('/') || from.startsWith('//') || from.includes('\\')) return home
  const isAdminArea = from === '/admin' || from.startsWith('/admin/')
  if (role === 'ADMIN') return isAdminArea ? from : home
  return isAdminArea ? home : from
}
