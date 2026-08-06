# Test matrix

Measurement method: automated Gradle test tasks; physical-device rows are recorded when executed and remain unchecked until then.

Automated verification run on 2026-08-06 from the repository root:

- `./gradlew test --no-configuration-cache --no-daemon --console=plain` — passed.
- `./gradlew :app:lintDebug checkModuleDependencyRules :app:assembleRelease --no-configuration-cache --no-daemon --console=plain` — passed after the Markdown/PDF addition; lint report at `app/build/reports/lint-results-debug.html` and 24 MiB unsigned APK at `app/build/outputs/apk/release/app-release-unsigned.apk`.
- Merged release manifest inspected at `app/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml`; only Orator playback permissions plus the generated non-exported receiver permission remain. No `INTERNET`, `ACCESS_NETWORK_STATE`, broad-storage, media-storage, or boot-start permission is present.

## Automated unit and Robolectric tests (JVM)

| Area | Module | Task |
|---|---|---|
| Domain model invariants | :domain | `./gradlew :domain:test` |
| Use-case contracts | :domain | `./gradlew :domain:test` |
| Room DAOs: cascade delete, unique SHA-256, foreign keys, atomic rollback | :data | `./gradlew :data:testDebugUnitTest` (Robolectric, in-memory SQLite) |
| Progress validation against paragraphs | :data | `./gradlew :data:testDebugUnitTest` (Robolectric) |
| TXT parser golden tests (UTF-8/16, BOM, CRLF, normalization, malformed) | :data | `./gradlew :data:testDebugUnitTest` |
| Markdown parser tests (UTF-8/16, heading sections, inline syntax, fenced-code exclusion, weak-MIME resolution) | :data | `./gradlew :data:testDebugUnitTest` |
| EPUB parser golden tests (EPUB 2/3, spine order, nav labels, encrypted, traversal, limits) | :data | `./gradlew :data:testDebugUnitTest` |
| PDF parser tests (signature validation, embedded title, page-order text extraction) | :data | `./gradlew :data:testDebugUnitTest` (Robolectric with merged PDFBox assets) |
| Parser registry evidence rules (extensions, weak provider MIME, strong-claim conflicts) | :data | `./gradlew :data:testDebugUnitTest` |
| Metadata normalization (control chars, 200-unit truncation, surrogate safety) | :data | `./gradlew :data:testDebugUnitTest` |
| Narration state machine (fake engine/clock/audio focus/environment) | :playback | `./gradlew :playback:testDebugUnitTest` |
| Stale-callback rejection, pause-safety, retry-once, focus rules, wake-lock ownership | :playback | `./gradlew :playback:testDebugUnitTest` |
| Voice selection rules and title placeholder logic | :playback | `./gradlew :playback:testDebugUnitTest` |

## Physical-device scenarios (pending execution)

| Scenario | Device model | Android version | TTS engine | Fixture | Result |
|---|---|---|---|---|---|
| AC-001 UTF-8 TXT import | | | | | |
| AC-002 UTF-16 TXT import | | | | | |
| AC-003 EPUB 2/3 import | | | | | |
| AC-004 duplicate import | | | | | |
| AC-005 invalid sources | | | | | |
| AC-006 source removed | | | | | |
| AC-007 basic narration | | | | | |
| AC-008 pause/resume | | | | | |
| AC-009 process death checkpoint | | | | | |
| AC-010 force-stop | | | | | |
| AC-011 reboot | | | | | |
| AC-012 30-minute screen-off | | | | | |
| AC-013 audio interruption | | | | | |
| AC-014 route removal | | | | | |
| AC-015 TTS failure retry | | | | | |
| AC-016 API 26+ word highlight | | | | | |
| AC-017 API 24–25 segment highlight | | | | | |
| AC-018 follow mode | | | | | |
| AC-019 sentence navigation | | | | | |
| AC-020 100 MiB book paging | | | | | |
| AC-021 completed restart | | | | | |
| AC-022 speech setting change while playing | | | | | |
| AC-023 appearance persistence | | | | | |
| AC-024 200% font scaling | | | | | |
| AC-025 TalkBack navigation | | | | | |
| AC-026 offline flows | | | | | |
| AC-027 permission audit | | | | | |
| AC-028 Markdown import and narration text | | | | | |
| AC-029 two-page text PDF import | | | | | |
| AC-030 encrypted/image-only/malformed/limit PDF rejection | | | | | |

## Performance budgets (release build, pending)

| Budget | Target | Result |
|---|---|---|
| Cold launch to first usable Library frame (Pixel 6, median of 20) | ≤ 2.0 s with 100 records | |
| First utterance within 750 ms of Play after TTS init | ≤ 750 ms | |
| SpokenRange published ≤ 100 ms after onRangeStart | ≤ 100 ms | |
| 100 MiB TXT import without OOM, peak PSS < 256 MiB | pass | |
| Reader scroll slow frames < 5% over 30 s | pass | |
| 30-minute screen-off playback, ≤ 1 segment progress loss | pass | |
| No wake lock / focus / receiver 10 s after Idle/Paused/Completed/Error | pass | |
| Zero network requests from Orator UID during acceptance flows | pass | |

## Quality gates

- `./gradlew testDebugUnitTest` — all automated suites above (covered by the `test` run above)
- `./gradlew lint` — no unapproved errors (the app debug lint task passed; library lint is included in the release lint-vital checks)
- `./gradlew assembleRelease` — release build succeeds
- `./gradlew checkModuleDependencyRules` — forbidden lower-layer project dependencies rejected
