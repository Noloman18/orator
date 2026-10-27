# Orator Privacy Policy

**Effective date:** 27 October 2026

> Publishing checklist: replace `[developer name]` and `[privacy email]` before
> hosting this policy. Host it at a public HTTPS URL and add that URL to the app's
> in-app privacy-policy entry before submitting to Google Play.

Orator is operated by **[developer name]**. For privacy questions, contact
**[privacy email]**.

## What Orator does

Orator imports documents selected by you and displays or narrates their text. You
can attach typed notes and voice notes to a saved place in an imported document.
The core reader and narration are designed to work offline with the text-to-speech
engine and voices installed on your Android device.

You can also create a translated text copy of a document. Translation is offered
for supported languages with installed offline voices. Missing language packs
download from Google over Wi-Fi unless you enable mobile data; installed packs
can then be used offline. The original and translated copies remain separate.

The Book Notes screen can display a footer advertisement through the Google Mobile
Ads SDK. This release is configured with production ad identifiers. Before it is
published, the developer must configure and test the applicable consent and
privacy-options flow and ensure this policy matches the final setup.

## Information processed on your device

Orator may process the following information locally:

- Documents you choose to import, including their text and filename.
- Document text and translated text processed by on-device machine translation.
- Library metadata and reading progress.
- Your reader settings, including voice, speech rate, pitch, text size, line height,
  follow mode, and theme.
- Typed notes and voice-note recordings that you choose to create. Voice recordings
  are made only after you start recording in the note composer.
- Text passed to the installed Android text-to-speech engine so it can speak the
  selected passage.

The selected source document is copied into Orator's app-private storage during
import. The original document URI is not retained after import. Document contents,
titles, utterance text, and private file paths are not logged in production.

## Information Orator does not collect or share

Orator does not have an account system and does not send document content, reading
progress, settings, typed notes, or voice-note recordings to the developer or the
advertising SDK. The app does not include its own analytics or crash-reporting SDK.

The Google Mobile Ads SDK can make network requests to load the Book Notes footer
ad. According to Google's current SDK disclosure, it automatically collects and
shares IP address, product-interaction information, diagnostic information, and
device or account identifiers for advertising, analytics, and fraud prevention.
Google's disclosure is available at
<https://developers.google.com/admob/android/privacy/play-data-disclosure>. All
Google Mobile Ads SDK data is encrypted in transit according to that disclosure.

Google's ML Kit translation processes text locally and does not upload input text
or translated output to Google. It can contact Google for models and updates and
send SDK metrics. Google's disclosure lists device/app metadata, installation
identifiers, performance and error events, input/output sizes, and chosen source
and target languages for diagnostics and usage analytics. These metrics use HTTPS;
Google says ML Kit does not transfer them to third parties. Translation also uses
Firebase Remote Config and Installations, which process installation identifiers
and device/app configuration metadata. See
[ML Kit privacy](https://developers.google.com/ml-kit/terms#privacy),
[ML Kit data disclosure](https://developers.google.com/ml-kit/android-data-disclosure),
and [Firebase data disclosure](https://firebase.google.com/docs/android/play-data-disclosure).

Orator records microphone audio only when you explicitly start a voice note. It uses
an installed text-to-speech engine; eligible offline voices can narrate without a
network connection.

## Storage, deletion, and backup

You can remove an imported document from the Orator library. This removes its local
copy, associated library data, typed notes, and voice-note recordings. Uninstalling
Orator removes its app-private data under Android's normal rules.

Translated copies can be deleted independently. Removing an original does not
remove its translated copies. Downloaded language packs can remain after a book
is deleted and are removed when Orator's app data is cleared or the app is
uninstalled.

Android backup is limited to Orator settings. Imported document files, the library
database, and reading progress are excluded from backup and device transfer. A
restored installation may therefore require you to import documents again.

## Permissions

Orator uses the Android system document picker rather than broad storage access. It
uses foreground media playback and wake-lock capabilities to continue narration when
the screen is off. It requests microphone permission only when you choose to record
a voice note. It uses `INTERNET` and network-state access so the Book Notes ad can
load, translation language packs can download, and SDK diagnostics can be sent.
It does not request location, contacts, camera, or broad storage permissions.

## Children's privacy

Orator does not knowingly collect personal information from children and has no
account system. Advertising configuration and any age-related treatment must be
reviewed before a production release.

## Changes to this policy

If Orator's data practices change, this policy will be updated before the change is
released. Questions can be sent to **[privacy email]**.
