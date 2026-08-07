# Play Store docs

Release artifacts for the Google Play listing of the Orator app.

## Structure

- `screenshots/` — store listing screenshots (PNG/JPG; phone 2:3 or 9:16 recommended)
- `release-notes/` — release notes, one file per version, e.g. `1.1.txt`

## Play Store listing notes

- Current version: 1.1 (versionCode 2, see `app/build.gradle.kts`)
- Release notes should describe the new features in 500 characters or fewer.
- The signed upload bundle is produced by `./gradlew buildSignedBundle` and copied to `app/release/app-release.aab`.
