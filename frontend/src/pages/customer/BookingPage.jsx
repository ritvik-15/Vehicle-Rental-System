import { useEffect, useRef, useState } from 'react'
import { Link, useParams, useSearchParams, useNavigate } from 'react-router-dom'
import { createRental } from '../../services/rentalService.js'
import { getVehicle } from '../../services/vehicleService.js'
import { checkAvailability } from '../../services/vehicleService.js'
import { createPaymentOrder, verifyPayment, simulatePayment } from '../../services/paymentService.js'
import { validatePromo } from '../../services/promoService.js'
import VehicleImage from '../../components/VehicleImage.jsx'
import ErrorAlert from '../../components/ErrorAlert.jsx'

export default function BookingPage() {
  const { id } = useParams()
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()

  // Parse dates from URL query params
  const urlPickup = searchParams.get('pickupDateTime') || ''
  const urlReturn = searchParams.get('returnDateTime') || ''

  const [vehicle, setVehicle] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [notFound, setNotFound] = useState(false)
  const [backendDown, setBackendDown] = useState(false)
  const [retryTick, setRetryTick] = useState(0)

  const [pickup, setPickup] = useState(urlPickup)
  const [ret, setRet] = useState(urlReturn)
  const [notes, setNotes] = useState('')

  // Promo code state (optional; the backend validates eligibility and
  // computes the discount authoritatively at rental creation)
  const [promoCode, setPromoCode] = useState('')
  const [promoPreview, setPromoPreview] = useState(null)
  const [validatingPromo, setValidatingPromo] = useState(false)
  const validatingPromoRef = useRef(false)

  const [checkResult, setCheckResult] = useState(null)
  const [checking, setChecking] = useState(false)
  const checkingRef = useRef(false)
  const seqRef = useRef(0)

  // Rental creation state
  const [creatingRental, setCreatingRental] = useState(false)
  const creatingRentalRef = useRef(false)
  const [rental, setRental] = useState(null)

  // Payment order state
  const [creatingOrder, setCreatingOrder] = useState(false)
  const creatingOrderRef = useRef(false)
  const [paymentOrder, setPaymentOrder] = useState(null)

  // Razorpay checkout state
  const [checkoutActive, setCheckoutActive] = useState(false)
  const checkoutRef = useRef(false)

  // Verification state
  const [verifying, setVerifying] = useState(false)
  const verifyingRef = useRef(false)
  const [paymentResult, setPaymentResult] = useState(null)

  // Final confirmation state (after successful payment verification)
  const [confirmation, setConfirmation] = useState(null)

  // Errors
  const [submitError, setSubmitError] = useState('')

  // Simulated payment state
  const [simulatingPayment, setSimulatingPayment] = useState(false)
  const simulatingPaymentRef = useRef(false)
  const [simulationResult, setSimulationResult] = useState(null)
  // Chosen demo outcome (SUCCESS default) and its unsuccessful result. Kept
  // separate from simulationResult so the retry buttons stay visible.
  const [simulatedStatus, setSimulatedStatus] = useState('SUCCESS')
  const [simulationOutcome, setSimulationOutcome] = useState(null)

  // Client-side date validation
  function validateDates() {
    if (!pickup) return 'Pickup date and time is required.'
    if (!ret) return 'Return date and time is required.'
    const p = new Date(pickup)
    const r = new Date(ret)
    if (Number.isNaN(p.getTime())) return 'Pickup date and time is invalid.'
    if (Number.isNaN(r.getTime())) return 'Return date and time is invalid.'
    if (p.getTime() <= Date.now()) return 'Pickup date and time must be in the future.'
    if (r.getTime() <= p.getTime()) return 'Return must be after pickup.'
    return ''
  }

  // Invalidate stale availability and promo preview when dates change
  const onDateChange = (setter) => (e) => {
    setter(e.target.value)
    setCheckResult(null)
    setPromoPreview(null)
  }

  const onNotesChange = (e) => {
    const v = e.target.value
    if (v.length <= 2000) setNotes(v)
  }

  // Final availability check before booking
  const onFinalCheck = async () => {
    if (checkingRef.current) return
    const problem = validateDates()
    if (problem) {
      setSubmitError(problem)
      return
    }
    checkingRef.current = true
    setChecking(true)
    setCheckResult(null)
    setSubmitError('')
    const seq = ++seqRef.current
    try {
      const data = await checkAvailability(id, pickup, ret)
      if (seq === seqRef.current) setCheckResult(data)
    } catch (err) {
      if (seq === seqRef.current) {
        setBackendDown(err?.status === 0)
        setSubmitError(err?.message || 'Availability check failed.')
      }
    } finally {
      checkingRef.current = false
      setChecking(false)
    }
  }

  // Optional promo pre-validation. The preview request uses an estimated
  // pre-discount subtotal; the backend re-validates with a row lock and
  // computes the authoritative discount during rental creation.
  const onValidatePromo = async () => {
    if (validatingPromoRef.current) return
    const problem = validateDates()
    if (problem) {
      setSubmitError(problem)
      return
    }
    const code = promoCode.trim()
    if (!code) {
      setSubmitError('Enter a promo code first.')
      return
    }
    if (!vehicle?.dailyRate) {
      setSubmitError('Vehicle pricing is not loaded yet. Please try again.')
      return
    }
    validatingPromoRef.current = true
    setValidatingPromo(true)
    setSubmitError('')
    setPromoPreview(null)
    try {
      const seconds = (new Date(ret) - new Date(pickup)) / 1000
      const estimatedDays = Math.max(1, Math.ceil(seconds / 86400))
      const estimatedAmount = Number(vehicle.dailyRate) * estimatedDays
      const data = await validatePromo({ code, rentalAmount: estimatedAmount })
      setPromoPreview(data)
    } catch (err) {
      setSubmitError(err?.message || 'Promo code validation failed.')
    } finally {
      validatingPromoRef.current = false
      setValidatingPromo(false)
    }
  }

  // Step 1: Create rental
  const onCreateRental = async () => {
    if (creatingRentalRef.current) return
    const problem = validateDates()
    if (problem) {
      setSubmitError(problem)
      return
    }
    if (checkResult && !checkResult.available) {
      setSubmitError('Vehicle is not available for the selected dates.')
      return
    }
    creatingRentalRef.current = true
    setCreatingRental(true)
    setSubmitError('')
    try {
      const data = await createRental({ vehicleId: Number(id), pickupDateTime: pickup, returnDateTime: ret, notes, promoCode })
      setRental(data)
    } catch (err) {
      if (err?.status === 409) {
        setSubmitError(err?.message || 'Vehicle is no longer available for the selected period.')
        try {
          const data = await checkAvailability(id, pickup, ret)
          setCheckResult(data)
        } catch { /* ignore */ }
      } else {
        setSubmitError(err?.message || 'Booking failed. Please try again.')
      }
    } finally {
      creatingRentalRef.current = false
      setCreatingRental(false)
    }
  }

  // Step 2: Create payment order
  const onCreateOrder = async () => {
    if (creatingOrderRef.current) return
    creatingOrderRef.current = true
    setCreatingOrder(true)
    setSubmitError('')
    try {
      const data = await createPaymentOrder({ rentalId: rental.rentalId })
      setPaymentOrder(data)
    } catch (err) {
      if (err?.status === 400 && err?.message?.includes('expired')) {
        setSubmitError('Payment session has expired. Please create a new booking.')
        setRental(null)
      } else {
        setSubmitError(err?.message || 'Failed to create payment order. Please try again.')
      }
    } finally {
      creatingOrderRef.current = false
      setCreatingOrder(false)
    }
  }

  // Step 3: Open Razorpay Checkout
  const openCheckout = () => {
    if (!paymentOrder || checkoutRef.current) return
    if (typeof window === 'undefined' || !window.Razorpay) {
      setSubmitError('Payment gateway script failed to load. Please refresh the page.')
      return
    }
    checkoutRef.current = true
    setCheckoutActive(true)

    const options = {
      key: paymentOrder.keyId,
      amount: paymentOrder.amountPaise,
      currency: paymentOrder.currency,
      order_id: paymentOrder.razorpayOrderId,
      handler: function (response) {
        // Razorpay success callback - now verify with backend
        checkoutRef.current = false
        setCheckoutActive(false)
        onVerifyPayment(response)
      },
      modal: {
        ondismiss: function () {
          checkoutRef.current = false
          setCheckoutActive(false)
          onCheckoutDismissed()
        }
      },
      theme: { color: '#2563eb' }
    }

    const rzp = new window.Razorpay(options)
    rzp.on('payment.failed', function (response) {
      checkoutRef.current = false
      setCheckoutActive(false)
      onCheckoutFailed(response)
    })
    rzp.open()
  }

  // Step 4: Verify payment
  const onVerifyPayment = async (response) => {
    if (verifyingRef.current) return
    const { razorpay_order_id, razorpay_payment_id, razorpay_signature } = response
    if (!razorpay_order_id || !razorpay_payment_id || !razorpay_signature) {
      setSubmitError('Incomplete payment response. Please try again.')
      return
    }
    verifyingRef.current = true
    setVerifying(true)
    setSubmitError('')
    try {
      const data = await verifyPayment({
        razorpayOrderId: razorpay_order_id,
        razorpayPaymentId: razorpay_payment_id,
        razorpaySignature: razorpay_signature,
      })
      setPaymentResult(data)
      setConfirmation(data)
    } catch (err) {
      if (err?.status === 400) {
        // Payment failed or cancelled - Razorpay status will be reflected
        setSubmitError(err?.message || 'Payment verification failed.')
        setPaymentResult({ paymentStatus: 'FAILED', error: err?.message })
      } else if (err?.status === 409) {
        setSubmitError('This payment has already been processed.')
        setPaymentResult({ paymentStatus: 'DUPLICATE', error: err?.message })
      } else {
        setSubmitError(err?.message || 'Payment verification failed. Please try again.')
        setPaymentResult({ paymentStatus: 'ERROR', error: err?.message })
      }
    } finally {
      verifyingRef.current = false
      setVerifying(false)
    }
  }

  // Checkout dismissed (user closed modal)
  const onCheckoutDismissed = () => {
    setSubmitError('Payment was cancelled. You can try again when ready.')
  }

  // Checkout failed (Razorpay reports failure)
  const onCheckoutFailed = (response) => {
    if (response?.error) {
      setSubmitError(`Payment failed: ${response.error.description || response.error.reason || 'Unknown error'}`)
    } else {
      setSubmitError('Payment failed. Please try again.')
    }
  }

  // Retry payment order creation
  const onRetryOrder = () => {
    if (!rental) return
    setSubmitError('')
    onCreateOrder()
  }

  // Simulate payment (demo) with the selected outcome
  const onSimulatePayment = async () => {
    if (simulatingPaymentRef.current) return
    simulatingPaymentRef.current = true
    setSimulatingPayment(true)
    setSubmitError('')
    try {
      const data = await simulatePayment({ rentalId: rental.rentalId, simulatedStatus })
      // HTTP 200 carries the recorded outcome - interpret it from the body,
      // not from the transport status. Success uses the existing card.
      if (data?.paymentStatus === 'SUCCESS') {
        setSimulationResult(data)
        setSimulationOutcome(null)
      } else {
        setSimulationOutcome({
          paymentStatus: data?.paymentStatus,
          rentalStatus: data?.rentalStatus,
          message: data?.message,
        })
      }
    } catch (err) {
      setSubmitError(err?.message || 'Simulated payment failed. Please try again.')
    } finally {
      simulatingPaymentRef.current = false
      setSimulatingPayment(false)
    }
  }

  // If dates changed after rental created, invalidate
  useEffect(() => {
    if (rental) return
    if (urlPickup !== pickup || urlReturn !== ret) {
      setPickup(urlPickup)
      setRet(urlReturn)
      setCheckResult(null)
    }
  }, [urlPickup, urlReturn, pickup, ret, rental])

  // Load vehicle details
  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError('')
    setNotFound(false)
    setBackendDown(false)
    getVehicle(id)
      .then((data) => {
        if (cancelled) return
        setVehicle(data)
        setLoading(false)
      })
      .catch((err) => {
        if (cancelled) return
        setVehicle(null)
        setLoading(false)
        if (err?.status === 404) {
          setNotFound(true)
          setError('Vehicle not found. It may have been removed or is not available.')
        } else {
          setBackendDown(err?.status === 0)
          setError(err?.message || 'Failed to load the vehicle.')
        }
      })
    return () => { cancelled = true }
  }, [id, retryTick])

  if (loading) return <p className="state-note">Loading booking…</p>

  return (
    <section>
      <p>
        <Link to={`/home/vehicles/${id}`} className="back-link">← Back to Vehicle Details</Link>
      </p>

      {error && (
        <div>
          <ErrorAlert message={error} />
          {backendDown && (
            <p className="muted">The backend server appears to be offline. Please try again shortly.</p>
          )}
          {!notFound && (
            <button type="button" onClick={() => setRetryTick((t) => t + 1)}>Try again</button>
          )}
        </div>
      )}

      {!error && vehicle && !rental && !confirmation && (
        <div className="booking-layout">
          <div className="booking-vehicle card">
            <h2>Vehicle</h2>
            <div className="booking-vehicle-row">
              <VehicleImage images={vehicle.images} alt={`${vehicle.brand} ${vehicle.model}`} className="booking-vehicle-img" />
              <div>
                <h3>{vehicle.brand} {vehicle.model}</h3>
                <p className="muted">{vehicle.registrationNumber}</p>
                <p className="badges">
                  <span className="badge">{vehicle.vehicleType}</span>
                  <span className="badge">{vehicle.fuelType}</span>
                  <span className="badge">{vehicle.transmission}</span>
                </p>
              </div>
            </div>
          </div>

          <div className="card booking-form">
            <h2>Booking Summary</h2>

            <div className="control">
              <label htmlFor="pickup-dt">Pickup date & time</label>
              <input
                id="pickup-dt"
                type="datetime-local"
                value={pickup}
                onChange={onDateChange(setPickup)}
              />
            </div>

            <div className="control">
              <label htmlFor="return-dt">Return date & time</label>
              <input
                id="return-dt"
                type="datetime-local"
                value={ret}
                onChange={onDateChange(setRet)}
              />
            </div>

            <div className="control">
              <label htmlFor="notes">Notes (optional)</label>
              <textarea
                id="notes"
                value={notes}
                onChange={onNotesChange}
                rows={3}
                maxLength={2000}
                placeholder="Any special requests or notes for the rental…"
              />
              <p className="muted">{notes.length}/2000</p>
            </div>

            <div className="control">
              <label htmlFor="promo-code">Promo code (optional)</label>
              <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>
                <input
                  id="promo-code"
                  type="text"
                  value={promoCode}
                  maxLength={50}
                  onChange={(e) => {
                    setPromoCode(e.target.value)
                    setPromoPreview(null)
                  }}
                  placeholder="Enter promo code"
                />
                <button
                  type="button"
                  onClick={onValidatePromo}
                  disabled={validatingPromo}
                >
                  {validatingPromo ? 'Validating…' : 'Validate'}
                </button>
              </div>
            </div>

            {promoPreview && (
              <p className="success-alert" role="status" aria-live="polite">
                Promo {promoPreview.code} applied — discount ₹{promoPreview.discountAmount}
                {' '}(estimated final amount ₹{promoPreview.finalAmount}).
                Final pricing is confirmed by the backend at booking.
              </p>
            )}

            <button
              type="button"
              className="button-primary"
              onClick={onFinalCheck}
              disabled={checking}
            >
              {checking ? 'Checking availability…' : 'Check availability'}
            </button>

            {checkResult && (
              <p
                className={checkResult.available ? 'success-alert' : 'error-alert'}
                role="status"
                aria-live="polite"
              >
                {checkResult.message}
                <span className="muted"> {' '}(vehicle status: {checkResult.vehicleStatus})</span>
              </p>
            )}

            {submitError && <ErrorAlert message={submitError} />}

            {checkResult?.available && !creatingRental && !rental && (
              <button
                type="button"
                className="button-success"
                onClick={onCreateRental}
                disabled={creatingRental}
              >
                {creatingRental ? 'Creating rental…' : 'Pay & Confirm'}
              </button>
            )}

            <p className="muted small-note">
              This creates a rental and initiates payment.
            </p>
          </div>
        </div>
      )}

      {/* Creating rental state */}
      {!error && rental && !paymentOrder && !confirmation && !paymentResult && !simulationResult && (
        <div className="card confirmation">
          <h2>Rental Created</h2>
          <p className="success-alert">Rental #{rental.rentalId} created successfully.</p>
          
          <dl className="confirmation-details">
            <div><dt>Vehicle</dt><dd>{rental.vehicleBrand} {rental.vehicleModel} ({rental.vehicleRegistrationNumber})</dd></div>
            <div><dt>Pickup</dt><dd>{rental.pickupDateTime?.replace?.('T', ' ') || rental.pickupDateTime}</dd></div>
            <div><dt>Return</dt><dd>{rental.returnDateTime?.replace?.('T', ' ') || rental.returnDateTime}</dd></div>
            <div><dt>Duration</dt><dd>{rental.numberOfDays} day{rental.numberOfDays !== 1 ? 's' : ''}</dd></div>
            <div><dt>Daily rate</dt><dd>₹{rental.dailyRate}</dd></div>
            <div><dt>Subtotal</dt><dd>₹{rental.subtotal}</dd></div>
            {rental.discountAmount && rental.discountAmount > 0 && (
              <div><dt>Discount{rental.promoCode ? ` (${rental.promoCode})` : ''}</dt><dd>-₹{rental.discountAmount}</dd></div>
            )}
            <div><dt>Tax (18%)</dt><dd>₹{rental.taxAmount}</dd></div>
            <div><dt>Security deposit (20%)</dt><dd>₹{rental.securityDeposit}</dd></div>
            <div className="total-row"><dt>Total</dt><dd>₹{rental.totalAmount}</dd></div>
            <div><dt>Status</dt><dd><span className={`badge badge-${(rental.rentalStatus || '').toLowerCase()}`}>{rental.rentalStatus}</span></dd></div>
          </dl>

          {submitError && <ErrorAlert message={submitError} />}

          <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap', marginTop: '1rem' }}>
            <button
              type="button"
              className="button-primary"
              onClick={onCreateOrder}
              disabled={creatingOrder}
            >
              {creatingOrder ? 'Creating payment order…' : 'Proceed to Razorpay Payment'}
            </button>

            <button
              type="button"
              className="button-success"
              onClick={onSimulatePayment}
              disabled={simulatingPayment}
            >
              {simulatingPayment ? 'Processing…' : 'Simulate Payment (Demo)'}
            </button>
          </div>

          <div className="confirmation-details" style={{ marginTop: '1rem' }}>
            <div>
              <dt>Simulated outcome</dt>
              <dd>
                <select
                  value={simulatedStatus}
                  onChange={(e) => setSimulatedStatus(e.target.value)}
                  disabled={simulatingPayment}
                >
                  <option value="SUCCESS">Success (payment recorded, rental confirmed)</option>
                  <option value="FAILED">Failed (attempt recorded, rental stays pending)</option>
                  <option value="CANCELLED">Cancelled (attempt recorded, rental stays pending)</option>
                </select>
              </dd>
            </div>
          </div>

          <p className="muted small-note">
            <strong>SIMULATED DEMO MODE:</strong> The "Simulate Payment (Demo)" button records the selected outcome without any real payment gateway. No real money is charged. For real payments, use "Proceed to Razorpay Payment".
          </p>

          {simulationOutcome && (
            <div className="card confirmation" style={{ marginTop: '1rem' }}>
              <h2>
                {simulationOutcome.paymentStatus === 'CANCELLED'
                  ? 'Simulated Payment Cancelled'
                  : 'Simulated Payment Failed'}
              </h2>
              <p className="error-alert">
                {simulationOutcome.message ||
                  'SIMULATED DEMO PAYMENT - No real money was charged. The attempt was recorded and the rental is still PENDING.'}
              </p>
              <p className="muted small-note">
                The booking is still <strong>{simulationOutcome.rentalStatus || 'PENDING'}</strong>.
                You can retry with another simulated outcome or continue with Razorpay Payment within 15 minutes.
              </p>
            </div>
          )}
        </div>
      )}

      {/* Creating payment order state */}
      {!error && rental && !paymentOrder && creatingOrder && (
        <div className="card confirmation">
          <h2>Creating Payment Order…</h2>
          <p className="state-note">Please wait while we create your payment order.</p>
        </div>
      )}

      {/* Payment order created - ready for checkout */}
      {!error && paymentOrder && !checkoutActive && !verifying && !paymentResult && (
        <div className="card confirmation">
          <h2>Ready for Payment</h2>
          <p className="success-alert">Payment order created successfully.</p>
          
          <dl className="confirmation-details">
            <div><dt>Rental ID</dt><dd>#{rental.rentalId}</dd></div>
            <div><dt>Vehicle</dt><dd>{rental.vehicleBrand} {rental.vehicleModel} ({rental.vehicleRegistrationNumber})</dd></div>
            <div><dt>Amount</dt><dd>₹{(paymentOrder.amountPaise / 100).toFixed(2)}</dd></div>
            <div><dt>Currency</dt><dd>{paymentOrder.currency}</dd></div>
            <div><dt>Order ID</dt><dd><code>{paymentOrder.razorpayOrderId}</code></dd></div>
          </dl>

          <button
            type="button"
            className="button-success"
            onClick={openCheckout}
            disabled={checkoutActive}
          >
            {checkoutActive ? 'Opening payment…' : 'Pay Now'}
          </button>

          <p className="muted small-note">
            You will be redirected to Razorpay Checkout. After payment, you will return here for verification.
          </p>
        </div>
      )}

      {/* Checkout active */}
      {!error && checkoutActive && (
        <div className="card confirmation">
          <h2>Payment in Progress</h2>
          <p className="state-note">Complete the payment in the Razorpay window. Do not close this page.</p>
        </div>
      )}

      {/* Verifying payment */}
      {!error && verifying && (
        <div className="card confirmation">
          <h2>Verifying Payment…</h2>
          <p className="state-note">Please wait while we verify your payment with the gateway.</p>
        </div>
      )}

      {/* Payment result - success/failed/cancelled/error */}
      {paymentResult && !error && (
        <div className={`card confirmation ${paymentResult.paymentStatus === 'SUCCESS' ? 'success' : 'failure'}`}>
          <h2>{paymentResult.paymentStatus === 'SUCCESS' ? 'Payment Successful' : 'Payment Not Completed'}</h2>
          
          {paymentResult.paymentStatus === 'SUCCESS' ? (
            <p className="success-alert">Your payment has been verified and the booking is confirmed.</p>
          ) : (
            <p className="error-alert">
              {paymentResult.error || 'Payment could not be completed.'}
            </p>
          )}

          <dl className="confirmation-details">
            <div><dt>Rental ID</dt><dd>#{rental?.rentalId || paymentResult.rentalId}</dd></div>
            <div><dt>Vehicle</dt><dd>{rental?.vehicleBrand} {rental?.vehicleModel} ({rental?.vehicleRegistrationNumber})</dd></div>
            <div><dt>Pickup</dt><dd>{rental?.pickupDateTime?.replace?.('T', ' ') || rental?.pickupDateTime}</dd></div>
            <div><dt>Return</dt><dd>{rental?.returnDateTime?.replace?.('T', ' ') || rental?.returnDateTime}</dd></div>
            <div><dt>Duration</dt><dd>{rental?.numberOfDays} day{rental?.numberOfDays !== 1 ? 's' : ''}</dd></div>
            <div><dt>Daily rate</dt><dd>₹{rental?.dailyRate}</dd></div>
            <div><dt>Subtotal</dt><dd>₹{rental?.subtotal}</dd></div>
            {rental?.discountAmount && rental.discountAmount > 0 && (
              <div><dt>Discount</dt><dd>-₹{rental.discountAmount}</dd></div>
            )}
            <div><dt>Tax (18%)</dt><dd>₹{rental?.taxAmount}</dd></div>
            <div><dt>Security deposit (20%)</dt><dd>₹{rental?.securityDeposit}</dd></div>
            <div className="total-row"><dt>Total</dt><dd>₹{rental?.totalAmount}</dd></div>
            <div><dt>Payment Status</dt><dd><span className={`badge badge-${(paymentResult.paymentStatus || '').toLowerCase()}`}>{paymentResult.paymentStatus}</span></dd></div>
            <div><dt>Rental Status</dt><dd><span className={`badge badge-${(paymentResult.rentalStatus || '').toLowerCase()}`}>{paymentResult.rentalStatus || rental?.rentalStatus}</span></dd></div>
            {paymentResult.razorpayPaymentId && <div><dt>Razorpay Payment ID</dt><dd>{paymentResult.razorpayPaymentId}</dd></div>}
          </dl>

          <div className="confirmation-actions">
            {paymentResult.paymentStatus === 'SUCCESS' ? (
              <>
                <Link to="/home" className="button">Back to Vehicles</Link>
                <Link to={`/home/vehicles/${id}`} className="button-secondary">View Vehicle</Link>
              </>
            ) : (
              <>
                {rental?.rentalStatus === 'PENDING' && !creatingOrder && (
                  <button type="button" className="button-primary" onClick={onRetryOrder}>
                    Try Payment Again
                  </button>
                )}
                <Link to="/home" className="button-secondary">Back to Vehicles</Link>
              </>
            )}
          </div>

          <p className="muted small-note">
            {paymentResult.paymentStatus === 'SUCCESS' 
              ? 'Rental status: CONFIRMED. Your booking is complete.'
              : 'Rental remains PENDING. You can retry payment if the session has not expired.'
            }
          </p>
        </div>
      )}

      {/* Simulated payment result */}
      {simulationResult && !error && (
        <div className="card confirmation success">
          <h2>Simulated Payment Successful</h2>
          <p className="success-alert">
            {simulationResult.message || 'SIMULATED DEMO PAYMENT — No real money was charged. Payment recorded as DEMO SUCCESS.'}
          </p>

          <dl className="confirmation-details">
            <div><dt>Demo Reference</dt><dd>{simulationResult.demoReference}</dd></div>
            <div><dt>Rental ID</dt><dd>#{simulationResult.rentalId}</dd></div>
            <div><dt>Vehicle</dt><dd>{rental?.vehicleBrand} {rental?.vehicleModel} ({rental?.vehicleRegistrationNumber})</dd></div>
            <div><dt>Pickup</dt><dd>{rental?.pickupDateTime?.replace?.('T', ' ') || rental?.pickupDateTime}</dd></div>
            <div><dt>Return</dt><dd>{rental?.returnDateTime?.replace?.('T', ' ') || rental?.returnDateTime}</dd></div>
            <div><dt>Duration</dt><dd>{rental?.numberOfDays} day{rental?.numberOfDays !== 1 ? 's' : ''}</dd></div>
            <div><dt>Daily rate</dt><dd>₹{rental?.dailyRate}</dd></div>
            <div><dt>Subtotal</dt><dd>₹{rental?.subtotal}</dd></div>
            {rental?.discountAmount && rental.discountAmount > 0 && (
              <div><dt>Discount</dt><dd>-₹{rental.discountAmount}</dd></div>
            )}
            <div><dt>Tax (18%)</dt><dd>₹{rental?.taxAmount}</dd></div>
            <div><dt>Security deposit (20%)</dt><dd>₹{rental?.securityDeposit}</dd></div>
            <div className="total-row"><dt>Total</dt><dd>₹{rental?.totalAmount}</dd></div>
            <div><dt>Payment Status</dt><dd><span className="badge badge-success">DEMO SUCCESS</span></dd></div>
            <div><dt>Rental Status</dt><dd><span className={`badge badge-${(simulationResult.rentalStatus || '').toLowerCase()}`}>{simulationResult.rentalStatus}</span></dd></div>
          </dl>

          <div className="confirmation-actions">
            <Link to="/home" className="button">Back to Vehicles</Link>
            <Link to={`/home/vehicles/${id}`} className="button-secondary">View Vehicle</Link>
          </div>

          <p className="muted small-note">
            <strong>DEMO MODE:</strong> This payment was simulated for demonstration purposes. No real payment was processed. The rental is now CONFIRMED.
          </p>
        </div>
      )}
    </section>
  )
}