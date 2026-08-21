#!/usr/bin/env python3
"""Compose Drake-style comparison creatives (iterations 2-4) at 1200x1500."""
from PIL import Image, ImageDraw, ImageFont
import os

ROOT = "ad-campaign/creative"
FONT_DIR = "/usr/share/fonts/truetype/dejavu"


def font(size):
    return ImageFont.truetype(os.path.join(FONT_DIR, "DejaVuSans-Bold.ttf"), size)


def caption(draw, cx, y, s, fnt_size):
    f = font(fnt_size)
    bb = draw.textbbox((0, 0), s, font=f)
    tw = bb[2] - bb[0]
    th = bb[3] - bb[1]
    x0 = cx - tw // 2 - 40
    y0 = y - 14
    ov = Image.new("RGBA", draw._image.size, (0, 0, 0, 0))
    ImageDraw.Draw(ov).rounded_rectangle(
        (x0, y0, x0 + tw + 80, y0 + th + 28), radius=20, fill=(10, 26, 27, 130))
    draw._image.alpha_composite(ov)
    draw.text((cx, y), s, font=f, fill=(255, 255, 255), anchor="ma")


def wordmark(draw, icon):
    size = 30
    ic = icon.resize((size, size), Image.LANCZOS)
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, size, size), radius=6, fill=255)
    draw._image.paste(ic, (520, 1456), mask)
    draw.text((560, 1468), "Orator", font=font(24), fill=(255, 255, 255), anchor="la")


def main():
    icon = Image.open("playstore-docs/graphics/app-icon-512.png").convert("RGBA")
    jobs = [
        ("07-meme-commuter.png", "07-commuter-portrait-1200x1500.png",
         "Reading on the commute", "Listening on the commute"),
        ("08-meme-student.png", "08-student-portrait-1200x1500.png",
         "Struggling with documents", "Letting Orator read them"),
        ("09-meme-night.png", "09-night-portrait-1200x1500.png",
         "Tired eyes, small text", "Eyes closed, still reading"),
    ]
    for src, dst, top, bottom in jobs:
        base = Image.open(os.path.join(ROOT, "base", src)).convert("RGBA")
        up = base.resize((1500, 1500), Image.LANCZOS)
        img = up.crop((150, 0, 1350, 1500)).copy()
        d = ImageDraw.Draw(img)
        d.rounded_rectangle((0, 744, 1200, 756), radius=6, fill=(255, 255, 255))
        caption(d, 600, 668, top, 38)
        caption(d, 600, 1372, bottom, 38)
        wordmark(d, icon)
        img.convert("RGB").save(os.path.join(ROOT, dst))
        print("saved", dst)


if __name__ == "__main__":
    main()
