# Google Play Data safety worksheet

This is source material for the Play Console Data safety form, not a replacement for
the form. It must be completed for the exact AAB submitted. The current source
uses Google's sample Mobile Ads identifiers for test-device installs. Before a
production submission, switch to the live identifiers and use the guidance below.

## Current live-ad build: review required before submission

### Does your app collect or share any required user data types?

**Do not submit “No.”** Google Mobile Ads SDK is bundled for the Book Notes footer.
Google's current disclosure for the SDK says it automatically collects and shares
IP address, product-interaction information, diagnostic information, and device and
account identifiers for advertising, analytics, and fraud prevention. The developer
is responsible for the final answers, including any data introduced by the exact
production ad configuration:
<https://developers.google.com/admob/android/privacy/play-data-disclosure>.

Before publishing, audit the resulting AAB, configure and test the applicable UMP
consent and privacy-options flow, and complete the Data safety form from Google's
current guidance.

### On-device translation SDK

The app also bundles `com.google.mlkit:translate:17.0.3`. Text and translated output
are processed locally, while language-pack downloads and SDK diagnostics use the
network. Google's [ML Kit disclosure](https://developers.google.com/ml-kit/android-data-disclosure)
lists device/app information, installation identifiers, performance/configuration
metrics, input/output sizes, events/errors, and source/target languages for
diagnostics and usage analytics. Include these in the submitted build's answers;
local translation does not imply that the SDK collects no data. ML Kit encrypts
these metrics with HTTPS and reports no third-party transfer.

Translate also uses Firebase Remote Config and Installations. Account for their
installation identifiers and device/app configuration metadata using the
[Firebase disclosure](https://firebase.google.com/docs/android/play-data-disclosure#remote-config).
Source text and translated text are not uploaded, as confirmed by
[ML Kit's privacy terms](https://developers.google.com/ml-kit/terms#privacy).

### Data types handled locally but not transmitted

These are useful for reviewing the form, but should not be marked as collected or
shared under Google's off-device definition unless a future version transmits them:

- Files and documents: imported document content and filenames.
- Translations: document text and saved translated copies; translation inference
  runs on the device after the required language packs have downloaded.
- App activity: library state, reading position, reader settings, and typed notes.
- Audio-related processing: text is passed to the installed text-to-speech engine;
  voice notes are recorded only when the user starts recording and are kept in
  private app storage.

### Security and deletion notes

- Imported document content and private notes are not supplied by Orator to the ad
  request. The Google Mobile Ads SDK makes its own network requests; Google's SDK
  disclosure states that its collected data is encrypted in transit with TLS.
- Do not claim that local files are encrypted at rest unless that implementation is
  added and verified.
- Users can delete imported documents from the library, which also removes their
  associated typed and voice notes. Uninstalling the app removes app-private data;
  there is no developer-held account data to delete.
- Translated copies are deleted independently of their originals. Language packs
  remain available for other books until app data is cleared or Orator is
  uninstalled.

## Console reminders

- Complete the form for the exact AAB submitted, including all bundled libraries.
- Keep the answers consistent with the hosted [privacy policy](privacy-policy.md).
- Configure and test the required consent and privacy-options flow before any
  production advertising release.
- Re-submit the form whenever a release changes network services or third-party
  SDKs.

Official guidance: <https://support.google.com/googleplay/android-developer/answer/10787469>
