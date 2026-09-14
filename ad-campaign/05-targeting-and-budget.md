# Targeting and budget

Settings for the first Google App campaign (install objective).

## Campaign setup

| Setting | Value |
| --- | --- |
| Campaign type | App campaign (install goal) |
| App | Orator — `com.noloxtreme.tts.reader` |
| Conversion | Installs (Google Play conversion reported by Google) |
| Bid strategy | Install volume with target CPI only after confirming a sustainable target from user value |
| Starting target CPI | $1.50 only if the economics support it; otherwise use Google Ads bid guidance |
| Daily budget | At least 50× target CPI for a target-CPI install campaign ($75/day at a $1.50 target) |
| Inventory | Google automatically serves across Search, Google Play, YouTube, Discover, and the Display Network, including other apps |

The previous $15/day proposal is not sufficient for a $1.50 target-CPI campaign.
If $75/day is not available, do not treat early performance as conclusive; either
defer the paid launch or select a campaign/bid approach that Google Ads supports
for the available budget.

## Locales (launch)

English-speaking markets where the store listing exists (en-US) and Android
devices commonly have offline TTS voices:

1. United States — highest volume, anchor market
2. United Kingdom
3. Canada
4. Australia
5. Ireland
6. New Zealand
7. South Africa (brand relevance; lower volume, cheap installs)

Do not expand beyond this until the CPI and retention data from
`06-measurement-and-testing.md` say it is safe. Later expansions (in priority
order): Germany, Netherlands, Nordic markets, then India (after checking voice
availability and pricing).

## Targeting settings

- **No audience targeting at launch.** App campaigns optimize on their own with
  install signals; manual audiences shrink the learning pool. Let Google find
  readers and accessibility users.
- **Avoid restrictive exclusions at launch.** App campaigns optimize across
  Google's inventory; review policy and invalid-traffic signals rather than
  making broad placement exclusions from a small data sample.
- **Dayparting:** do not enable. App campaigns manage delivery; dayparting
  fights the algorithm and raises CPI.
- **Ad rotation:** not configurable in App campaigns; refresh creatives instead
  (see measurement plan).

## Budget guardrails

- Check whether the campaign is marked "Limited by budget" or "Limited by
  target" before changing creative or bids.
- Allow 7–14 days after a meaningful bid, budget, or conversion-action change
  before judging performance. Evaluate an updated campaign over a 30-day window
  where possible.
- Never change a bid or budget by more than ±20% at once; large swings restart
  the learning phase.

Official reference: [Google Ads App campaign goal and budget guidance](https://support.google.com/google-ads/answer/6167156).
