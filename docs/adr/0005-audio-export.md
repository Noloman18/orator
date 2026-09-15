# ADR 0005 — User-initiated audio export (AAC)

Status: Accepted  
Date: 2026-08-26

## Context

Readers want a finished audio file of a book for offline listening outside Orator. ADR 0001 and the v1 spec (Section 2.2) forbid permanent audio generation, so this decision explicitly amends them for a single, user-initiated feature. An export must not block the app: synthesizing a whole book takes minutes to hours, so the job runs in the background and survives the user leaving the app or turning off the screen.

## Decision

Add a user-initiated **Export audio…** action that synthesizes a full document into one AAC-LC (`.m4a`) file on device storage:

- The job is a WorkManager `CoroutineWorker` elevated to a foreground service (`mediaProcessing` type on Android 15+, `dataSync` below) so the OS does not reap it. Jobs are deduplicated per document and cancellable from the notification or the reader menu.
- The worker reads paragraph text from Room (never through WorkManager input data, which is capped at 10 KB), chunks it with the narration sentence-boundary rules (max 3000 characters), and synthesizes each chunk via `synthesizeToFile` on a dedicated `TextToSpeech` instance — so an export can run while live narration plays on its own engine.
- Long books are processed in bounded, sentence-aligned batches (about 10,000 words, or a 384 MiB normalized-WAV cap). Each batch WAV is normalized to the same 22.05 kHz mono 16-bit PCM format before Media3 encodes it to AAC, then deleted. This tolerates device TTS engines changing their PCM format, sample rate or channel count across utterances. The temporary AAC batches are remuxed into one M4A by copying AAC samples into a fresh MP4 container, avoiding a giant source WAV, a second lossy encode, and invalid byte-concatenation of M4A containers. Media3 negotiates a supported AAC encoder configuration for the device rather than forcing a profile or bitrate that a vendor codec may reject. No FFmpeg or other native code.
- The finished file is published to Music/Orator via MediaStore (API 29+; legacy shared Music directory on API 24–28). A previous export of the same book is replaced, not duplicated.
- Progress and completion surface through the foreground notification (with a cancel action) and, while the app is open, the same top-bar progress pattern used for imports.
- All temporary WAVs, AAC batches and remux output live under one cache work directory. It is deleted on success, failure and cancellation; a fresh attempt also clears remnants left by process death or reboot.
- Only offline voices are eligible; the export uses the same voice, rate, and pitch settings as live narration.

This amends ADR 0001's "no audio files are generated or persisted" and spec Section 2.2's permanent-audio-generation exclusion. Those statements remain true for everything except this explicit export feature.

## Consequences

- Books can be turned into playable AAC files entirely offline; no network permission is added.
- Exports are CPU- and storage-heavy: a long book can take hours to synthesize, but only one bounded WAV batch is retained at a time. The foreground service and cleanup guarantees make this safe, while the progress notification shows synthesis, batch encoding, final combination and saving separately.
- Narration and export each bind their own TTS engine; the export engine isolates synthesis failures from live playback.
- New manifest permissions: `FOREGROUND_SERVICE_MEDIA_PROCESSING`, `FOREGROUND_SERVICE_DATA_SYNC`, `POST_NOTIFICATIONS` (runtime on Android 13+), and legacy `WRITE_EXTERNAL_STORAGE` (API ≤ 28).
- Exported files live in shared storage and belong to the user: they are excluded from app backup and are not deleted when the book is removed from Orator.
