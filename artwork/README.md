# Orator artwork

The editable master is `orator-logo-master.svg`. It is intentionally a simple book-and-wave mark so it remains legible as a launcher icon, notification icon, splash symbol, and small document type indicator.

- Primary background: `#285E61`.
- Foreground: `#FFFFFF` for adaptive and notification contexts.
- Keep the complete mark inside the central 66% safe area of a 108×108 adaptive-icon viewport.
- Minimum clear space is 8% of the canvas on every side.
- Do not add text to launcher or notification variants; book titles use the deterministic title placeholder instead.
- The source SVG is the design master. Android vector drawables are the runtime assets; raster store exports must be generated from this master without changing proportions or colors.
