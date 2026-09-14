# AdMob configuration — internal release reference

This is an operational reference for the developer. It is not store-listing copy,
privacy-policy content, or in-app user-facing text.

AdMob application and ad-unit IDs are identifiers rather than credentials; never
place an AdMob account password, API key, or service-account file in this repository.

## Current development configuration

The app deliberately uses Google's sample IDs while the Book Notes footer ad is
being tested:

- App ID: `ca-app-pub-3940256099942544~3347511713`
- Anchored adaptive banner unit ID: `ca-app-pub-3940256099942544/9214589741`

## Production Book Notes footer

Use these only when ready to serve live ads:

- App ID: `ca-app-pub-1951746776607933~4729235966`
- Banner unit ID: `ca-app-pub-1951746776607933/2591439047`

## Before switching to production

1. Replace both test IDs: the manifest app ID and the Book Notes banner-unit
   constant. They must belong to the same AdMob app.
2. Keep test IDs in all development and QA builds; do not generate artificial
   impressions or clicks on the production banner.
3. Complete the applicable consent and privacy-options flow, then review the
   [privacy policy](../playstore-docs/privacy-policy.md) and
   [Data safety worksheet](../playstore-docs/data-safety.md) against the exact
   production AAB.
4. Build and test the production-configured AAB on a real device. A newly created
   ad unit can take up to an hour before it begins serving.
