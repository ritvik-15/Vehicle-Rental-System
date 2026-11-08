import { api } from './api.js'

// Phase D: thin payment helper over the existing api.js
// Exact backend contract from Phase 8:
// POST /api/payments/create-order -> { rentalId }
// POST /api/payments/verify -> { razorpay_order_id, razorpay_payment_id, razorpay_signature }
// POST /api/payments/simulate -> { rentalId } (demo only)

export async function createPaymentOrder({ rentalId }) {
  return api.post('/api/payments/create-order', { rentalId })
}

export async function verifyPayment({ razorpayOrderId, razorpayPaymentId, razorpaySignature }) {
  return api.post('/api/payments/verify', {
    razorpay_order_id: razorpayOrderId,
    razorpay_payment_id: razorpayPaymentId,
    razorpay_signature: razorpaySignature,
  })
}

export async function simulatePayment({ rentalId, simulatedStatus }) {
  const body = { rentalId }
  if (simulatedStatus !== undefined) body.simulatedStatus = simulatedStatus
  return api.post('/api/payments/simulate', body)
}