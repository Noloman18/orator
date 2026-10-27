# Orator

Orator is a Kotlin/Compose Android app for reading documents aloud with the
device's text-to-speech engine.

## Translation

Open a book's reader menu and choose **Translate book** to create a separate
translated text copy in the library. The original stays intact. The copy retains
chapter divisions and supports reading, narration, notes, and audio export; EPUB
images and page styling are not copied.

Orator checks the device before enabling translation. Both the source and target
language must be supported by translation and have an installed offline TTS voice.
Cloud voices and voices whose data is still downloading do not qualify. The
availability screen links to Android voice settings and lets users check again.
Translated copies always use a voice matching their target language.

In a translated copy's narration menu, **Language learning** shows the current
original and translated passage together: stacked on phones and side by side on
wider screens. Both panes follow the translated narration. The original uses an
approximate phrase highlight, since translations can change word order and
sentence length. Scrolling pauses visual following; **Follow narration** catches
both panes up without seeking the audio. The original book must remain in the
library. This view is available only during narration, not in silent Read Mode.

Translation runs locally using ML Kit Translate 17.0.3. On first use, missing
language packs download over Wi-Fi unless the user enables mobile data. Once the
packs are installed, translation works offline. Progress, cancellation, and retry
states are shown in the translation sheet.

Machine translation is powered by [Google Translate](https://translate.google.com),
using its on-device models. Automatic translations can contain errors. See
[Cloud Translation](https://cloud.google.com/translate) and Google's
[attribution requirements](https://docs.cloud.google.com/translate/attribution).
The reader and translation sheet display Google Translate attribution. Input
text and translated text stay on the device; the SDK can send diagnostics and
usage metrics, as described in the [privacy policy](playstore-docs/privacy-policy.md).

## Prerequisites

- JDK 17 or newer
- Android SDK

## Building a signed release bundle

The build machine must have the following environment variables set so the
release bundle can be signed:

| Variable | Description |
| --- | --- |
| `ANDROID_BUILD_KEYSTORE` | Path to the keystore file, or its base64-encoded content |
| `ANDROID_BUILD_KEYSTORE_ALIAS` | Key alias inside the keystore |
| `ANDROID_BUILD_KEYSTORE_PASSWORD` | Password for the keystore and key |

Example (shell):

```sh
export ANDROID_BUILD_KEYSTORE=/path/to/your-keystore.jks
export ANDROID_BUILD_KEYSTORE_ALIAS=upload
export ANDROID_BUILD_KEYSTORE_PASSWORD=your-password
```

Then build the signed bundle:

```sh
./gradlew buildSignedBundle
```

The signed `.aab` is copied to `app/release/app-release.aab`.

If the environment variables are not set, the task fails with a clear error
message. Debug builds and unsigned release builds are unaffected.

## Code coverage

Generate the aggregate JaCoCo HTML and XML reports from the JVM and Android
unit tests with:

```sh
./gradlew jacocoTestReport
```

Open the report at
`build/reports/jacoco/jacocoTestReport/html/index.html`. The verification task
checks that aggregate line coverage remains at least 50%:

```sh
./gradlew jacocoTestCoverageVerification
```

The report covers the testable production logic in `app`, `data`, `domain`,
and `playback`. It excludes generated Android/Hilt/Room/Dagger classes,
Compose and Android entry points, and the service/notification adapters whose
behavior belongs in instrumented or device-level tests.
