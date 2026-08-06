# ADR 0003 — Character-position progress

Status: Accepted  
Date: 2026-08-06

## Context

TextToSpeech audio duration is not deterministic and changes with voice, rate, pitch, and engine. Progress measured in elapsed audio milliseconds cannot be mapped back to text reliably, and the spec forbids inventing audio durations.

## Decision

Reading progress is stored as a document position: paragraph index, character offset within the paragraph, and an absolute character offset computed over normalized paragraphs joined by exactly two newline characters. A safe resume position is committed to Room after every completed speech segment. The UI and media session never maintain a second authoritative playback cursor; standard media time seeking is intentionally disabled and character percentage is derived from absolute offsets.

## Consequences

- Progress survives process death, force-stop, and reboot because it is durable in Room.
- After an abrupt failure, narration repeats at most the active segment and never skips unheard text.
- Voice, rate, and pitch changes can always resume from a text position.
- The reader and playback layer share one position model, keeping both in sync.
