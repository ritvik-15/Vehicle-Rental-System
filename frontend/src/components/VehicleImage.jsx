import { useEffect, useState } from 'react'

// Vehicle image with primary selection and a clean fallback for
// no-image / broken-image cases (backend field: images[{imageUrl,isPrimary}]).
// Backend-provided images always win; when none is usable, a local
// type-specific placeholder is shown (CAR/BIKE/BUS/TRUCK), falling back to
// the original generic placeholder for unknown/missing types. Each tier
// fails at most once, so a failing image can never loop.
const TYPE_PLACEHOLDERS = {
  CAR: '/images/vehicle-car.svg',
  BIKE: '/images/vehicle-bike.svg',
  BUS: '/images/vehicle-bus.svg',
  TRUCK: '/images/vehicle-truck.svg',
}
const GENERIC_PLACEHOLDER = '/placeholder-vehicle.svg'

export default function VehicleImage({ images, alt, className, type }) {
  const list = Array.isArray(images) ? images : []
  const chosen =
    list.find((img) => img?.isPrimary && img?.imageUrl) || list.find((img) => img?.imageUrl)
  const placeholderSrc = TYPE_PLACEHOLDERS[type] || GENERIC_PLACEHOLDER
  const [broken, setBroken] = useState(false)
  const [placeholderBroken, setPlaceholderBroken] = useState(false)

  useEffect(() => {
    setBroken(false)
    setPlaceholderBroken(false)
  }, [chosen?.imageUrl, placeholderSrc])

  if (!chosen || broken) {
    return (
      <div className={`img-fallback ${className || ''}`} role="img" aria-label={alt || 'Vehicle'}>
        {!placeholderBroken && (
          <img
            className="img-fallback-art"
            src={placeholderSrc}
            alt=""
            onError={() => setPlaceholderBroken(true)}
          />
        )}
        {placeholderBroken && <span>No photo</span>}
      </div>
    )
  }
  return (
    <img
      className={className}
      src={chosen.imageUrl}
      alt={alt || 'Vehicle'}
      loading="lazy"
      onError={() => setBroken(true)}
    />
  )
}
