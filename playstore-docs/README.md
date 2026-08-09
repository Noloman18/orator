# Play Store docs

Release artifacts and submission notes for the Google Play listing of Orator.

## Repository assets

- `graphics/app-icon-512.png` — 512x512 32-bit PNG Play listing icon.
- `graphics/feature-graphic-1024x500.jpg` — 1024x500 feature graphic in the Roman visual style.
- `screenshots/phone/` — upload-ready 1080x2160 phone screenshots.
- `screenshots/` — original device captures, retained as source material.
- `listing/en-US.md` — English store listing copy.
- `release-notes/` — release notes, one file per version, e.g. `1.2.txt`.
- `privacy-policy.md` — privacy policy draft ready to host publicly after replacing its placeholders.
- `data-safety.md` — Data safety form worksheet based on the current source audit.
- `asset-inventory.md` — dimensions, alt text, and upload notes for each asset.
- `play-console-checklist.md` — remaining Play Console and release steps.

## Current release metadata

- App: `Orator`
- Application ID: `com.noloxtreme.tts.reader`
- Version: `1.2` (`versionCode 3`)
- Target API: 37
- Signed bundle task: `./gradlew buildSignedBundle`
- Bundle output: `app/release/app-release.aab`

The keystore, passwords, signed bundle, Play App Signing enrollment, and developer
account details belong outside the repository. The signed bundle task requires the
environment variables documented in the root [README](../README.md).

## Before publishing

The privacy policy must be hosted at a public HTTPS URL and its developer name and
contact email must be completed. The same URL or policy text must also be made
available from inside the app before submission. Complete the Data safety,
content-rating, target-audience, category, and developer-account sections in Play
Console; the repository files provide the source material but do not submit those
forms automatically.
