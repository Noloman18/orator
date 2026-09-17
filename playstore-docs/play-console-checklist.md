# Google Play submission checklist

## Ready in the repository

- [x] Signed-bundle Gradle task: `./gradlew buildSignedBundle`.
- [x] Release metadata: application ID `com.noloxtreme.tts.reader`, version `1.6.1`,
      `versionCode 10`, target API 37.
- [x] Play icon, feature graphic, and three upload-ready phone screenshots.
- [x] Foreground-service demo video published on YouTube — see
      [foreground-service-video.md](foreground-service-video.md).
- [x] English store listing copy and release notes for versions 1.0, 1.1, and 1.2.
- [x] Privacy policy draft and Data safety worksheet.

## Still required from the developer or Play Console

- [ ] Replace the privacy-policy placeholders with the legal/developer name and a
      monitored public privacy email.
- [ ] Host `privacy-policy.md` at a public HTTPS URL and expose the same policy
      URL or text inside the app.
- [ ] Create or select the Play Console app and complete developer identity/contact
      verification.
- [ ] Complete the category, target audience, content-rating questionnaire, and
      Data safety form.
- [x] Production Google Mobile Ads app and banner identifiers are configured.
- [ ] Configure and test the UMP consent and privacy-options flow, then re-check the
      store listing, privacy policy, and Data safety answers before publishing live ads.
- [ ] Enrol in Google Play App Signing and keep the upload keystore and passwords
      backed up securely outside the repository.
- [ ] Build the signed AAB with the release keystore and upload it to an internal or
      closed testing track before production.
- [ ] Upload the graphics and phone screenshots from this folder, then review the
      listing on phone and web surfaces.
- [ ] Add a fourth high-resolution phone screenshot if recommendation eligibility is
      important; three are present now and two are the minimum for publishing a
      basic listing.

## Build command

Set the three signing variables described in the root README, then run:

```sh
./gradlew buildSignedBundle
```

The task writes `app/release/app-release.aab`. Do not commit the keystore, signing
passwords, or generated bundle to the repository.

The app has no login or gated account, so a reviewer demo account is not expected.
