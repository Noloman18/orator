# Foreground-service demo video

Video used for the Play Console **foreground-service declaration**, which asks
for a link to a video showing the foreground service in use.

## Published link

Use this in the Play Console declaration field:

- https://www.youtube.com/watch?v=NI9TbnlnS8Y

The link originally shared from the YouTube app is
https://youtube.com/shorts/NI9TbnlnS8Y?feature=share (a Shorts URL). Prefer the
`watch?v=` form above when pasting into Play Console, since the declaration and
promo-video fields validate against standard YouTube video URLs.

## Source file

The uploaded video is committed in this repository:

```
playstore-docs/screenshots/foreground-video/Screen_Recording_20260827_125835_Orator.mp4
```

If the video ever needs to be re-uploaded or the link goes dead, upload that
file again and update the link above.

## Video facts (verified at upload time)

| Property | Value |
| --- | --- |
| Container | MP4 |
| Video codec | H.264 (Main profile), 1080x2340 portrait |
| Audio codec | AAC 48 kHz |
| Duration | 1 min 44 s |
| Size | ~29 MB |

The file is already in YouTube's recommended format (H.264 + AAC in MP4), so no
conversion is needed. Portrait orientation is fine for the foreground-service
declaration because it shows the real app UI; do not reuse this clip as the
store-listing promo video, where landscape is recommended.

## Uploading a replacement (instructions)

1. Go to https://www.youtube.com/upload and sign in.
2. Select `Screen_Recording_20260827_125835_Orator.mp4` from
   `playstore-docs/screenshots/foreground-video/`.
3. Set visibility to **Unlisted** (recommended — only people with the link,
   i.e. Play reviewers, can watch it; Public also works).
4. Add a title and optional description; thumbnail is not required and does not
   matter for an unlisted review video.
5. Upload and wait for processing to finish (a few minutes).
6. Copy the link (use the `https://www.youtube.com/watch?v=<id>` form) and
   paste it into the Play Console field that requested it.
7. Keep the video uploaded permanently — deleting it later breaks the store
   listing's player/review link.
