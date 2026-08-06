# Privacy and backup

## Privacy

- Orator requires no INTERNET permission and performs no network requests. All import, parsing, narration, and progress features work offline with an installed offline TTS voice.
- Books are selected by the user through Android's system document picker (Storage Access Framework). Orator requests no broad storage permission.
- The selected source is copied into app-private storage during import and the original URI is discarded; it is never persisted and no URI permission is retained.
- Document text, titles, source URIs, utterance text, and private file paths are never logged in production.
- No analytics or crash-reporting SDKs are included in v1.
- Orator uses the device's installed TextToSpeech engine; only offline-capable voices are eligible, and no speech audio is recorded or uploaded.

## Backup and device transfer

Android cloud backup and device-to-device transfer include only the user's settings:

- Included: `datastore/orator_settings.preferences_pb` (voice, rate, pitch, reader size, line height, follow mode, theme).
- Excluded: `databases/orator.db` and its journal files (book metadata, paragraphs, progress), `files/documents/` (imported originals), and any other app files.

This means a restored device shows the app's settings but the library is rebuilt by re-importing books. Backup rules live in `res/xml/backup_rules.xml` and `res/xml/data_extraction_rules.xml`, and `android:allowBackup="false"` is not used so that settings-only backup still applies on supported API levels.

## Permissions

- `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `WAKE_LOCK` — required for screen-off narration.
- No `INTERNET`, `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`, or storage permissions.

The only notification is attached to the active media session and is permission-exempt.
