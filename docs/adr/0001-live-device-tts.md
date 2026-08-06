# ADR 0001 — Live device TTS

Status: Accepted  
Date: 2026-08-06

## Context

Orator narrates imported books. The v1 specification requires narration to work fully offline and forbids cloud speech services, permanent audio generation, and network-based voices.

## Decision

Use the device's installed Android `TextToSpeech` engine for all narration. Utterances are submitted as bounded, sentence-aware segments, and only voices whose `Voice.isNetworkConnectionRequired` value is `false` are eligible. No audio files are generated or persisted. Missing voice data is never downloaded automatically; the app surfaces an install/configure action instead.

## Consequences

- Narration works offline once a compatible offline voice is installed.
- Voice quality depends on the user's installed engine; Orator cannot guarantee a specific voice.
- Sentence segmentation and character-position progress keep narration deterministic even though the engine's audio duration is unpredictable.
- No per-utterance or per-book speech cost, no network permission required.
