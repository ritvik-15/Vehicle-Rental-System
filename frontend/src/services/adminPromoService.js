import { api } from './api.js'

// Thin admin promo helper over the existing api.js.
// Exact backend contracts (verified in AdminPromoController/AdminPromoService):
//   GET  /api/admin/promos               -> 200 List<PromoCodeAdminResponse>
//        { code, discountType, discountValue, minimumAmount, maximumDiscount,
//          startDate, expiryDate, usageLimit, usedCount, status, createdAt }
//        ordered by createdAt DESC; no params, pagination or filters.
//   POST /api/admin/promos               -> 201 PromoCodeAdminResponse
//        body { code, discountType, discountValue, minimumAmount,
//               maximumDiscount?, startDate, expiryDate, usageLimit?, status? }
//        400 validation failure, 409 duplicate code.
//   PATCH /api/admin/promos/{code}/status -> 200 PromoCodeAdminResponse
//        body { status } — "ACTIVE" | "INACTIVE"; 400 blank/invalid or
//        unchanged status, 404 unknown code.
// All routes require the ADMIN role (SecurityConfig guards /api/admin/**);
// a 401 on a token-authenticated request is handled by api.js (session cleared).
export async function listPromoCodes() {
  return api.get('/api/admin/promos')
}

export async function createPromoCode(payload) {
  return api.post('/api/admin/promos', payload)
}

export async function updatePromoCodeStatus(code, status) {
  return api.patch(
    `/api/admin/promos/${encodeURIComponent(code)}/status`,
    { status },
  )
}
