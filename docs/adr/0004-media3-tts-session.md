# ADR 0004 — Media3 TTS session

Status: Accepted  
Date: 2026-08-06

## Context

Orator must continue narration while the screen is off and expose system media controls (notification, lock screen, headsets). TextToSpeech is not a Media3 player, so the two worlds need to be bridged without faking an audio timeline.

## Decision

Use a Media3 `MediaSessionService` (`NarrationService`) with a custom `TtsPlayer` that extends `SimpleBasePlayer` and maps Media3 player state and commands onto the narration state machine. The player contains exactly one `MediaItem` per loaded document, reports no invented duration, advertises Play, Pause, Stop, previous-sentence, and next-sentence commands, and is not seekable. A custom `MediaNotification.Provider` renders the Orator notification with the document title, current section as subtitle, the generated title-placeholder artwork, sentence-level controls, and custom five-sentence rewind/fast-forward actions. Audio focus, wake-lock, and noisy-route ownership live in the service/coordinator and are released whenever playback leaves the active states.

## Consequences

- Screen-off narration and system controls work through standard Media3 mechanisms.
- System UIs can never seek by time because the player is not seekable and reports no duration.
- Rewind and fast-forward remain text-aware five-sentence jumps rather than elapsed-time seeks.
- The media session stays in the same process as Room and the UI (single process, no IPC boundary).
- The notification is a media-session notification and is exempt from the POST_NOTIFICATIONS permission requirement.
