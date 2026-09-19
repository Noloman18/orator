# TTS Orator website deployment

Live address: `https://orator.segoo-inc.com/`

The site is served from a private S3 bucket through CloudFront, with HTTPS
enforced at the edge. Route 53 aliases `orator.segoo-inc.com` to the CloudFront
distribution. The production infrastructure was provisioned on 2026-09-19.

## Updating the site

Run this from the repository root after editing the files in this directory:

```sh
ORATOR_CLOUDFRONT_DISTRIBUTION_ID=EMUOK1RJRI12K ./website/deploy.sh
```

The script syncs the static files, gives `app-ads.txt`, `robots.txt`, and the
sitemap non-cacheable headers, then invalidates CloudFront.

## AdMob verification

`app-ads.txt` is intentionally at the site root:

```text
https://orator.segoo-inc.com/app-ads.txt
```

Its content authorizes the production AdMob publisher account:

```text
google.com, pub-1951746776607933, DIRECT, f08c47fec0942fa0
```

After the site is live, the remaining manual action is in Google Play Console:

1. Open **Grow users → Store presence → Store settings**.
2. Under **Store listing contact details**, set **Website** to
   `https://orator.segoo-inc.com`.
3. Wait for the public listing update, then wait at least 24 hours for AdMob.
4. In AdMob, select **Check for updates**, then **Verify app** when offered.

The privacy-policy URL can also be changed to
`https://orator.segoo-inc.com/privacy.html` so both public links live on the
same verified domain.
