# AdMob configuration — internal release reference

This is an operational reference for the developer. It is not store-listing copy,
privacy-policy content, or in-app user-facing text.

AdMob application and ad-unit IDs are identifiers rather than credentials; never
place an AdMob account password, API key, or service-account file in this repository.

## Current production configuration

The release configuration uses these live Book Notes footer identifiers:

- App ID: `ca-app-pub-1951746776607933~4729235966`
- Banner unit ID: `ca-app-pub-1951746776607933/2591439047`

## Development and QA configuration

Use Google's sample identifiers for any future development or QA build. Do not
generate artificial impressions or clicks on the production banner.

- Test app ID: `ca-app-pub-3940256099942544~3347511713`
- Test anchored adaptive banner unit ID: `ca-app-pub-3940256099942544/9214589741`

## Before publishing the live-ad bundle

1. Configure the European regulations message in AdMob Privacy & messaging, then
   integrate and test Google UMP consent collection and a persistent privacy-options
   entry point in the app. Do not publish the live-ad bundle before this is complete.
2. Keep test IDs in all development and QA builds; do not generate artificial
   impressions or clicks on the production banner.
3. Review the
   [privacy policy](../playstore-docs/privacy-policy.md) and
   [Data safety worksheet](../playstore-docs/data-safety.md) against the exact
   production AAB.
4. Build and test the production-configured AAB on a real device. A newly created
   ad unit can take up to an hour before it begins serving.
