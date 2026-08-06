# Architecture and SOLID review

Review date: 2026-08-06

This review records source-level evidence for the architecture checklist. Device-only behavior and release acceptance remain in `docs/TEST_MATRIX.md` until exercised on hardware.

| Principle or rule | Evidence | Status |
|---|---|---|
| Module dependency direction | `./gradlew checkModuleDependencyRules`; the root `checkModuleDependencyRules` task rejects `:domain`, `:data`, `:playback`, or `:designsystem` dependencies on forbidden layers. | Pass |
| Domain isolation | `:domain` is a Kotlin/JVM module; `rg` finds no Android SDK imports under `domain/src/main`. | Pass |
| UI boundary | `:app` ViewModels call domain use cases and ports; Room and `TextToSpeech` occur only in `:data` and `:playback`. | Pass |
| Playback boundary | `:playback` depends on `:domain` only and reaches content/progress/settings through domain interfaces. | Pass |
| Parser substitution | `DocumentImporter` selects the TXT or EPUB parser behind the parser block contract in `data/src/main/kotlin/com/noloxtreme/tts/reader/data/Importing.kt`; parser golden tests use both implementations. | Pass |
| Fake substitution | `playback/src/test/.../NarrationTestFakes.kt` implements the same `SpeechEngine`, repository, clock, focus, and environment ports used by `NarrationCoordinatorTest`. | Partial: production TTS is Android-only and still needs a device contract run |

The partial item is intentionally not checked in Section 24 until an Android-engine contract test is executed on a device.
