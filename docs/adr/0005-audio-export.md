# ADR 0005 — User-initiated audio export (AAC)

Status: Accepted  
Date: 2026-08-26

## Context

Readers want a finished audio file of a book for offline listening outside Orator. ADR 0001 and the v1 spec (Section 2.2) forbid permanent audio generation, so this decision explicitly amends them for a single, user-initiated feature. An export must not block the app: synthesizing a whole book takes minutes to hours, so the job runs in the background and survives the user leaving the app or turning off the screen.

## Decision

Add a user-initiated **Export audio…** action that synthesizes a full document into one AAC-LC (`.m4a`) file on device storage:

- The job is a WorkManager `CoroutineWorker` elevated to a foreground service (`mediaProcessing` type on Android 15+, `dataSync` below) so the OS does not reap it. Jobs are deduplicated per document and cancellable from the notification or the reader menu.
- The worker reads paragraph text from Room (never through WorkManager input data, which is capped at 10 KB), chunks it with the narration sentence-boundary rules (max 3000 characters), and synthesizes each chunk via `synthesizeToFile` on a dedicated `TextToSpeech` instance — so an export can run while live narration plays on its own engine.
- WAV chunks are concatenated and encoded to AAC-LC (mono, 22050 Hz, 64 kbps) with Media3 Transformer, a maintained AndroidX component. No FFmpeg or other native code.
- The finished file is published to Music/Orator via MediaStore (API 29+; legacy shared Music directory on API 24–28). A previous export of the same book is replaced, not duplicated.
- Progress and completion surface through the foreground notification (with a cancel action) and, while the app is open, the same top-bar progress pattern used for imports.
- Per-segment state on disk gives cheap resume after process death or reboot; user cancellation deletes partial work.
- Only offline voices are eligible; the export uses the same voice, rate, and pitch settings as live narration.

This amends ADR 0001's "no audio files are generated or persisted" and spec Section 2.2's permanent-audio-generation exclusion. Those statements remain true for everything except this explicit export feature.

## Consequences

- Books can be turned into playable AAC files entirely offline; no network permission is added.
- Exports are CPU- and storage-heavy: a long book can take hours to synthesize, and the WAV working set in cache is roughly 2.6 MB per minute of audio. The foreground service and resume state make this safe, but the progress notification is the user's window into it.
- Narration and export each bind their own TTS engine; the export engine isolates synthesis failures from live playback.
- New manifest permissions: `FOREGROUND_SERVICE_MEDIA_PROCESSING`, `FOREGROUND_SERVICE_DATA_SYNC`, `POST_NOTIFICATIONS` (runtime on Android 13+), and legacy `WRITE_EXTERNAL_STORAGE` (API ≤ 28).
- Exported files live in shared storage and belong to the user: they are excluded from app backup and are not deleted when the book is removed from Orator.
