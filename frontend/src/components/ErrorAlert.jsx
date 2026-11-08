// Minimal reusable API error display (no toast/notification framework).
export default function ErrorAlert({ message }) {
  if (!message) return null
  return (
    <p className="error-alert" role="alert">
      {message}
    </p>
  )
}
