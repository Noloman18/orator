#!/usr/bin/env bash
# Upload a new version of the already-provisioned TTS Orator website.
# This script intentionally does not create or modify infrastructure.
set -euo pipefail

site_root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
bucket="orator.segoo-inc.com"
distribution_id="${ORATOR_CLOUDFRONT_DISTRIBUTION_ID:?Set ORATOR_CLOUDFRONT_DISTRIBUTION_ID first}"

aws s3 sync "$site_root" "s3://$bucket/" \
  --delete \
  --exclude ".DS_Store" \
  --exclude "deploy.sh" \
  --exclude "DEPLOYMENT.md" \
  --cache-control "public, max-age=300" \
  --only-show-errors

# app-ads.txt and crawler instructions should never be stale.
aws s3 cp "$site_root/app-ads.txt" "s3://$bucket/app-ads.txt" \
  --content-type "text/plain; charset=utf-8" \
  --cache-control "no-cache, no-store, must-revalidate" \
  --only-show-errors
aws s3 cp "$site_root/robots.txt" "s3://$bucket/robots.txt" \
  --content-type "text/plain; charset=utf-8" \
  --cache-control "no-cache, no-store, must-revalidate" \
  --only-show-errors
aws s3 cp "$site_root/sitemap.xml" "s3://$bucket/sitemap.xml" \
  --content-type "application/xml; charset=utf-8" \
  --cache-control "no-cache, no-store, must-revalidate" \
  --only-show-errors

aws cloudfront create-invalidation \
  --distribution-id "$distribution_id" \
  --paths "/*" \
  --no-cli-pager \
  --output text
