# Measurement and testing

How to judge the campaign and keep improving it.

## KPIs and checkpoints

| Checkpoint | Metric | Decision rule |
| --- | --- | --- |
| Week 1 | Learn phase completes; impressions ramp | No changes to bid, budget, or targeting. Only review creative status. |
| Week 2 | CPI, install volume, ad strength | If ad strength < "Good", add the alternate headlines/descriptions from `02-ad-copy.md`. |
| Week 4 | CPI vs $1.50 target; CTR by asset | Promote/keep the top-performing assets; drop the bottom 20% and replace with alternates. |
| Week 8 | CPI, install rate, D30 retention | Keep or scale budget 20-25%; if CPI > $2.50, re-open `01-brief.md` assumptions. |

Lag time: Play-reported installs lag up to a few hours; retention data lags
30 days. Do not judge retention before it exists.

## A/B structure

Run the primary campaign as the "no-ads" theme (current material). After week 4,
clone it into a second campaign with the product-angle headlines/descriptions
swapped in and a 50/50 budget split for two weeks:

- Campaign A — "No ads" messaging (current copy).
- Campaign B — "Product" messaging (the Angle 3 alternates in `02-ad-copy.md`).

Keep both under the same target CPA and locale set so the only variable is
creative. Whichever wins on CPI wins the budget; the loser gets paused, not
deleted (it may win again after a creative refresh).

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
| High installs, low D30 retention | Wrong audience found by the algorithm | Try Campaign B theme; add a "no ads" clarification to the Play full description. |
| Sudden CPI spike > 3x average | Creative fatigue or invalid placement | Pause top-2 by spend, refresh creatives. |
| Store reviews mentioning "expected ads" | Positioning mismatch | Reinforce the no-ads promise on the listing short description. |

## Post-launch review (week 12)

Reassess the entire campaign against `01-brief.md`: CPI trend, install volume,
store conversion rate, and whether the no-ads claim is still true in the shipped
app (it must remain the honest differentiator). Decide: scale budget up to
$50/day, launch campaign B in new locales, or pause and fix the funnel first.
