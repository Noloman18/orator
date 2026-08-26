# Privacy and backup

## Privacy

- Orator requires no INTERNET permission and performs no network requests. All import, parsing, narration, and progress features work offline with an installed offline TTS voice.
- Books are selected by the user through Android's system document picker (Storage Access Framework). Orator requests no broad storage permission.
- The selected source is copied into app-private storage during import and the original URI is discarded; it is never persisted and no URI permission is retained.
- Document text, titles, source URIs, utterance text, and private file paths are never logged in production.
- No analytics or crash-reporting SDKs are included in v1.
- Orator uses the device's installed TextToSpeech engine; only offline-capable voices are eligible, and no speech audio is recorded or uploaded. The only audio the app generates is an explicit, user-initiated export to a local AAC file (see ADR 0005); nothing is sent off the device.
- Markdown conversion and PDF text extraction run locally. PDFBox-Android reads only the app-private copy; Orator does not execute PDF JavaScript or embedded media, attempt passwords, or send PDF content to an OCR/network service.

## Backup and device transfer

Android cloud backup and device-to-device transfer include only the user's settings:

- Included: `datastore/orator_settings.preferences_pb` (voice, rate, pitch, reader size, line height, follow mode, theme).
- Excluded: `databases/orator.db` and its journal files (book metadata, paragraphs, progress), `files/documents/` (imported originals), and any other app files.

This means a restored device shows the app's settings but the library is rebuilt by re-importing books. Backup rules live in `res/xml/backup_rules.xml` and `res/xml/data_extraction_rules.xml`; backup is enabled so the settings-only include can apply on supported API levels.

Exported audio files are the user's data in shared storage (`Music/Orator`). They are not part of app backup and are not removed when a book is deleted from Orator.

## Permissions

- `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `WAKE_LOCK` — required for screen-off narration.
- `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PROCESSING`, `FOREGROUND_SERVICE_DATA_SYNC` — required for background audio export (mediaProcessing on Android 15+, dataSync below).
- `POST_NOTIFICATIONS` — runtime permission (Android 13+) so export progress/completion notifications can show.
- `WRITE_EXTERNAL_STORAGE` (maxSdkVersion 28) — legacy shared-storage writes for exported audio on Android 7–9.
- No `INTERNET` permission, no network requests, no broad storage permission on Android 10+.

Notifications: the media-session notification (permission-exempt) and, while an export runs, the export progress and completion notifications.
