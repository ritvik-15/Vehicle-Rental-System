import { api } from './api.js'

// Vehicle-specific helpers over the Phase A API client (no second fetch wrapper).
// Exact backend contract: GET /api/vehicles, GET /api/vehicles/{id},
// GET /api/vehicles/{id}/availability?pickupDateTime&returnDateTime

const enc = encodeURIComponent

export function getVehicles({ search, vehicleType, fuelType, transmission, location, sortBy, sortDir } = {}) {
  const params = []
  if (search) params.push(`search=${enc(search)}`)
  if (vehicleType) params.push(`vehicleType=${enc(vehicleType)}`)
  if (fuelType) params.push(`fuelType=${enc(fuelType)}`)
  if (transmission) params.push(`transmission=${enc(transmission)}`)
  if (location) params.push(`location=${enc(location)}`)
  if (sortBy) params.push(`sortBy=${enc(sortBy)}`)
  if (sortDir) params.push(`sortDir=${enc(sortDir)}`)
  return api.get(`/api/vehicles${params.length ? `?${params.join('&')}` : ''}`)
}

export function getVehicle(vehicleId) {
  return api.get(`/api/vehicles/${enc(String(vehicleId))}`)
}

// Read-only availability check — never creates a rental, payment or any booking.
export function checkAvailability(vehicleId, pickupDateTime, returnDateTime) {
  return api.get(
    `/api/vehicles/${enc(String(vehicleId))}/availability` +
      `?pickupDateTime=${enc(pickupDateTime)}&returnDateTime=${enc(returnDateTime)}`,
  )
}
