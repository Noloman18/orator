# Creative assets

Upload-ready asset list for the App campaign. Official Google App campaign
image specs (per
[support.google.com/google-ads/answer/17091671](https://support.google.com/google-ads/answer/17091671)):

| Ratio | Recommended | Minimum | Max count |
| --- | --- | --- | --- |
| Horizontal 1.91:1 | 1200 x 628 | 600 x 314 | 20 images |
| Vertical 4:5 | 1200 x 1500 | 320 x 400 | 20 images |
| Square 1:1 | 1200 x 1200 | 200 x 200 | 20 images |

JPG or PNG, max 5 MB each. Videos are uploaded via YouTube (see below).

## Generated launch set (5 images)

Ready in `creative/` at exact upload dimensions:

| File | Size | Message |
| --- | --- | --- |
| `01-hero-landscape-1200x628.png` | 1200x628 | "No ads. Ever." + phone mockup of the real reader |
| `02-icon-square-1200x1200.png` | 1200x1200 | App icon block + "No intrusive ads" |
| `03-reader-portrait-1200x1500.png` | 1200x1500 | "Read without interruptions" + reader screenshot |
| `04-books-square-1200x1200.png` | 1200x1200 | "Your books, read aloud" + supported formats |
| `05-night-landscape-1200x628.png` | 1200x628 | "Peaceful reading" calm night scene |
| `06-drake-style-portrait-1200x1500.png` | 1200x1500 | Before/after meme: frustrated reading vs relaxed listening |
| `07-commuter-portrait-1200x1500.png` | 1200x1500 | Meme iteration: "Reading on the commute" vs "Listening on the commute" |
| `08-student-portrait-1200x1500.png` | 1200x1500 | Meme iteration: "Struggling with documents" vs "Letting Orator read them" |
| `09-night-portrait-1200x1500.png` | 1200x1500 | Meme iteration: "Tired eyes, small text" vs "Eyes closed, still reading" |

The phone screens in `01` and `03` contain the real reader screenshot from
`playstore-docs/screenshots/phone/03-reader.jpg`; the mark in `02` is the real
app icon. All overlays use headlines from `02-ad-copy.md`.

`06`-`09` use original cartoon characters in the Drake two-panel format — never
the real Drake likeness, which is a copyright risk in paid ads. Each iteration
features a different scenario (busy commuter, overwhelmed student, tired
night-time reader) with a caption pair from the approved copy tone.

Source backgrounds and the composition scripts live in `creative/base/`,
`creative/compose.py`, and `creative/compose-meme*.py` so the set can be
regenerated or restyled. Swap the headline overlays when refreshing the
campaign; the backgrounds are reusable.

## Video assets

At least one video recommended; two is better. Scripts: `03-video-scripts.md`.

| Orientation | Ratio | Recommended length | Notes |
| --- | --- | --- | --- |
| Landscape | 16:9 | 10-60s | Primary; used on YouTube in-stream and Search. |
| Portrait | 9:16 | 10-60s | Used in Discover and Play Store placements; keep text in center 60%. |
| Square | 1:1 | 10-60s | Secondary; can be cropped from landscape master. |

Videos must be uploaded to YouTube before they can be attached to the campaign.
Max 20 video assets per ratio.

## HTML5 / Playable assets

Official specs (per
[support.google.com/google-ads/answer/9981650](https://support.google.com/google-ads/answer/9981650)):

- Upload as `.zip`, max 5 MB, max 512 files per zip; up to 20 zips per ad group.
- Responsive/full-screen on devices; a mandatory orientation meta tag:
  `<meta name="ad.orientation" content="portrait">` (or `landscape` /
  `portrait,landscape`).
- Must contain `<!DOCTYPE html>`, `<html>`, `<body>`; explicit end tags only
  (no self-closing SVG tags).
- All resources via relative paths inside the zip; no external references
  except Google Fonts, Google-hosted jQuery/Greensock/CreateJS, and the
  `exitapi.js` click handler.
- Sound only after user interaction (these ads are silent by design).
- Billed per engagement (CPE), not per click; conversions count as installs
  within 30 days of the engagement.

### Generated playables (2)

| File | Orientation | Interaction |
| --- | --- | --- |
| `orator-html5-portrait.zip` | portrait (320x480 reference, responsive) | "Tap to listen": document card starts narrating with animated equalizer and sentence highlight, then the Install CTA (via `ExitApi.exit()`). |
| `orator-html5-landscape.zip` | landscape (480x320 reference, responsive) | "Trying to read vs listening instead": tap to switch from the frustrated reader panel to the relaxed listening panel, then Install. |

Source: `creative/html5/portrait/index.html` and
`creative/html5/landscape/index.html` (each with `icon.png`). Rebuild the zips
with `cd creative/html5/<name> && zip -r -j ../../orator-html5-<name>.zip .` —
`index.html` must sit at the zip root.

## Asset checklist before upload

1. All images are PNG or JPG, under 5 MB, RGB (no alpha for JPG).
2. No text is cut off at the edges in any crop Google may choose; the five
   generated finals keep all copy inside the frame with safe margins.
3. No contact info, badges, or ratings claims on the assets (Play policy).
4. Videos are MP4 (H.264), no soundtrack audio required but narration is fine,
   captions embedded or burned in.
5. Minimums met: landscape ≥ 600x314, portrait ≥ 320x400, square ≥ 200x200.

## Regenerating the creative set

The finals are composites: AI-generated teal backgrounds from `creative/base/`,
the real app icon, and the real reader screenshot, with the approved headlines
from `02-ad-copy.md` overlaid in DejaVu Sans Bold. The composition script is
`creative/compose.py`. To refresh creatives, swap the background images or
headline strings in the script and re-run it — the layout, phone mockup, and
brand styling are already consistent with `artwork/` guidelines.
