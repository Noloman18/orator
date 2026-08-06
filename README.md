# Orator

Orator is a Kotlin/Compose Android app for reading documents aloud with the
device's text-to-speech engine.

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
