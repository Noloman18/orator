# Measurement and testing

How to judge the campaign and keep improving it.

## KPIs and checkpoints

| Checkpoint | Metric | Decision rule |
| --- | --- | --- |
| Week 1 | Learn phase completes; impressions ramp | No changes to bid, budget, or targeting. Only review creative status. |
| Week 2 | CPI, install volume, asset coverage | Check the asset report; add missing text, image, or video types rather than reacting to early CPI. |
| Week 4 | CPI vs target; CTR and cost by asset | Add new variations for weak assets; keep delivery history until replacements have enough data. |
| Week 8 | CPI, install rate, D30 retention | Scale only if the campaign has sufficient budget and results support the target; otherwise change one constraint after learning. |

Lag time: Play-reported installs lag up to a few hours; retention data lags
30 days. Do not judge retention before it exists.

## Creative testing structure

Use one primary install campaign at launch. Do not clone a second App campaign
with the same geography just to test creative: the campaigns can compete for the
same users. Google also mixes text and visual assets, so judge individual asset
performance rather than treating every text/image pairing as a controlled A/B
test.

Keep two message families in the asset library:

- **Uninterrupted book** — ad-free reading and listening, no pop-ups, no ad
  breaks in a book.
- **Product** — document-to-speech, offline-capable narration, audio export,
  and book notes.

After an asset has enough delivery, add a replacement variation for a weak
asset. Only create a separate App campaign for a genuinely different optimization
goal or a non-overlapping geography.

## Creative refresh cadence

- **Every 4-6 weeks:** add one new image and one new video; retire the oldest
  unless it is the top performer. Stale creatives fatigue and raise CPI.
- **Every refresh:** re-shoot the video scripts' screen recordings if the app UI
  changed; outdated UI in ads causes listing mismatches and poor post-install
  behavior.
- **Seasonal:** one run in November-December (gifting/commuting/reading season)
  with a warmth-flavored cut of Script 3.

## Guardrail signals that something is wrong

| Signal | Likely cause | Action |
| --- | --- | --- |
| High CTR, low install rate | Listing mismatch or misleading ad | Compare ad claim vs listing; adjust the ad copy, not the listing. |
| High installs, low D30 retention | Wrong audience or weak first-use experience | Refresh with the product message and review onboarding/first-book completion. |
| Sudden CPI spike > 3x average | Creative fatigue, budget constraint, or a learning reset | Check campaign status and recent edits, then add a replacement asset. |
| Store reviews mentioning unexpected ads | Positioning mismatch | Make the Book Notes footer-ad disclosure clearer; never widen the ad-free claim. |

## Post-launch review (week 12)

Reassess the entire campaign against `01-brief.md`: CPI trend, install volume,
store conversion rate, retention where measured, and whether the uninterrupted-
book claim is still true in the shipped app. Decide: scale a budget that meets
Google's bid-to-budget guidance, expand to a new locale, or pause and fix the
funnel first.

Official reference: [Google Ads App campaign maintenance and asset guidance](https://support.google.com/google-ads/answer/9176652).
