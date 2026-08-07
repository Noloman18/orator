# Google Play Data safety worksheet

This is source material for the Play Console Data safety form, not a replacement for
the form. It is based on the current Orator v1.1 (`versionCode 2`) source and must be
reviewed again if analytics, crash reporting, advertising, online voices, accounts,
or network access are added.

## Recommended initial answers

### Does your app collect or share any required user data types?

**No**, based on the current implementation. Orator processes imported files,
library metadata, reading progress, settings, and narration text locally; it does
not transmit them off the device. There is no account system, analytics SDK, crash
reporting SDK, advertising SDK, or `INTERNET` permission.

### Data types handled locally but not transmitted

These are useful for reviewing the form, but should not be marked as collected or
shared under Google's off-device definition unless a future version transmits them:

- Files and documents: imported document content and filenames.
- App activity: library state, reading position, and reader settings.
- Audio-related processing: text is passed to the installed text-to-speech engine;
  Orator does not record or upload audio.

### Security and deletion notes

- There is no network transfer by Orator, so in-transit encryption is not applicable
  to Orator's own data flow.
- Do not claim that local files are encrypted at rest unless that implementation is
  added and verified.
- Users can delete imported documents from the library. Uninstalling the app removes
  app-private data; there is no developer-held account data to delete.

## Console reminders

- Complete the form for the exact AAB submitted, including all bundled libraries.
- Keep the answers consistent with the hosted [privacy policy](privacy-policy.md).
- Re-submit the form when a release adds network services or third-party SDKs.

Official guidance: <https://support.google.com/googleplay/android-developer/answer/10787469>
