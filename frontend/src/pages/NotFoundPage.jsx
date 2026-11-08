import { Link } from 'react-router-dom'

export default function NotFoundPage() {
  return (
    <div className="shell">
      <header className="topbar">
        <span className="brand">Vehicle Rental</span>
        <nav>
          <Link to="/">Home</Link>
        </nav>
      </header>
      <main className="content">
        <section className="card auth-card">
          <h1>Page not found</h1>
          <p className="muted">The page you requested does not exist.</p>
          <p>
            <Link className="button" to="/">Back to home</Link>
          </p>
        </section>
      </main>
    </div>
  )
}
