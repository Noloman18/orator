#!/usr/bin/env python3
"""Compose the Drake-style comparison creative at 1200x1500 (Google portrait)."""
from PIL import Image, ImageDraw, ImageFont, ImageFilter
import os

ROOT = "ad-campaign/creative"
FONT_DIR = "/usr/share/fonts/truetype/dejavu"
TEAL = (40, 94, 97)


def font(size, bold=True):
    name = "DejaVuSans-Bold.ttf" if bold else "DejaVuSans.ttf"
    return ImageFont.truetype(os.path.join(FONT_DIR, name), size)


def caption(draw, cx, y, s, fnt_size, width=980):
    """Caption with a dark translucent band, centered at cx."""
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


def main():
    icon = Image.open("playstore-docs/graphics/app-icon-512.png").convert("RGBA")
    base = Image.open(os.path.join(ROOT, "base", "06-meme-two-panels.png")).convert("RGBA")

    up = base.resize((1500, 1500), Image.LANCZOS)
    img = up.crop((150, 0, 1350, 1500)).copy()
    d = ImageDraw.Draw(img)

    d.rounded_rectangle((0, 744, 1200, 756), radius=6, fill=(255, 255, 255))

    caption(d, 600, 668, "Reading a long document", 40)
    caption(d, 600, 1372, "Listening to it read aloud", 40)

    size = 30
    ic = icon.resize((size, size), Image.LANCZOS)
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, size, size), radius=6, fill=255)
    d._image.paste(ic, (520, 1456), mask)
    d.text((560, 1468), "Orator", font=font(24), fill=(255, 255, 255), anchor="la")

    img.convert("RGB").save(os.path.join(ROOT, "06-drake-style-portrait-1200x1500.png"))
    print("done")


if __name__ == "__main__":
    main()
