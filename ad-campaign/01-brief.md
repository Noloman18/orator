# Campaign 01 — brief

## Objective

Drive installs of Orator from Google's App campaigns (install goal), growing
first-time users at a target cost per install while the Play listing and ratings
build credibility.

## Core message

> Orator reads your documents aloud without the intrusive ads that ruin most
> free apps. No ad breaks, no pop-ups, no account, no tracking — just calm,
> offline listening.

Everything else (EPUB/PDF/Markdown/TXT import, offline narration, sentence
highlighting) supports this message rather than replacing it. In a market where
"free" apps interrupt with ads every few minutes, Orator's promise is: *open a
document and listen — nothing else happens.*

## Primary audience

- People who read on mobile: commuters, students, language learners, low-vision
  and accessibility users, podcast lovers who want to read hands-free.
- People frustrated with free apps that push ads, subscriptions, or sign-ups.
- English-speaking markets with capable TTS voices installed (see
  `05-targeting-and-budget.md` for locale list).

## Message angles (ranked)

1. **No intrusive ads** — the differentiator. Leads every ad.
2. **No interruptions, no pop-ups** — the reading experience stays clean.
3. **Private and offline** — documents stay on the device, no account.
4. **Product capability** — turn any document into speech with voices already on the phone.

## Success metrics

| Metric | Target |
| --- | --- |
| Cost per install (CPI) | ≤ $1.50 blended after week 2 (see measurement plan) |
| Install rate from store page visits | ≥ 40% (Play conversion tracked by Google) |
| Ad strength score | "Good" or better at launch, "Excellent" within 4 weeks |
| Day-30 retention | ≥ 10% (checked in Play Console, lagged by 30 days) |
| Crash-free sessions | ≥ 99% (no ad-driven regression) |

## Launch checklist

1. Confirm Play listing (`playstore-docs/`) is live with the current screenshots
   and a 4.0+ rating or a plan to seed reviews through real users.
2. Set up the campaign per `05-targeting-and-budget.md`.
3. Upload the copy from `02-ad-copy.md` (all five headlines + five descriptions).
4. Upload image assets per `04-creative-assets.md` (at least 4).
5. Upload at least one video (scripts in `03-video-scripts.md`).
6. Connect Google Ads conversion tracking (installs + first open) if the app has
   Firebase or another measurement SDK; otherwise rely on Play-install
   conversions reported by Google.
7. Enable auto-applied recommendations only after week 2, and review each one.
