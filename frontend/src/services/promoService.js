import { api } from './api.js'

// Thin promo helper over the existing api.js
// Exact backend contract: POST /api/promos/validate
// Request: { code, rentalAmount } — rentalAmount is the pre-discount
// subtotal. The response's discountAmount and finalAmount are computed
// by the backend (authoritative); the client never supplies them.
export async function validatePromo({ code, rentalAmount }) {
  return api.post('/api/promos/validate', { code, rentalAmount })
}
