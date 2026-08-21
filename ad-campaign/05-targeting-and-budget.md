# Targeting and budget

Settings for the first Google App campaign (install objective).

## Campaign setup

| Setting | Value |
| --- | --- |
| Campaign type | App campaign (install goal) |
| App | Orator — `com.noloxtreme.tts.reader` |
| Conversion | Installs (Google Play conversion reported by Google) |
| Bid strategy | Target CPA (install), start $1.50, allow Google to raise to ~$2.00 in the first two weeks of learning |
| Daily budget | Start $15; raise to $25 once CPI is stable under target |
| Networks | All: Google Search, Google Play, YouTube, Discover, AdMob |

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
- **Exclude nothing initially** except obviously invalid placements (Mobvista
  and similar ad-network inventory is already filtered by Google; review the
  "Placements" report weekly and exclude any with abnormal CTR but near-zero
  installs).
- **Dayparting:** do not enable. App campaigns manage delivery; dayparting
  fights the algorithm and raises CPI.
- **Ad rotation:** not configurable in App campaigns; refresh creatives instead
  (see measurement plan).

## Budget guardrails

- Stop-and-fix thresholds: if CPI exceeds $4.00 for 5 consecutive days after
  week 2, cut to $10/day and refresh the weakest two headlines/descriptions.
- If installs average < 3/day after 2 weeks at $15/day, raise budget to $25 to
  give the algorithm more signal before judging it.
- Never change bid by more than ±20% in a day; big swings restart the learning
  phase.
