import { api } from './api.js'

// Phase C: thin rental helper over the existing api.js
// Exact backend contract: POST /api/rentals with RentalCreateRequest
// promoCode is optional: the backend resolves it with a pessimistic
// lock, validates eligibility and computes the discount authoritatively
// inside the same transaction as rental creation.
export async function createRental({ vehicleId, pickupDateTime, returnDateTime, notes, promoCode }) {
  const body = {
    vehicleId,
    pickupDateTime,
    returnDateTime,
    ...(notes?.trim() ? { notes: notes.trim() } : {}),
    ...(promoCode?.trim() ? { promoCode: promoCode.trim() } : {})
  }
  return api.post('/api/rentals', body)
}
