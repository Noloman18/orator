#!/usr/bin/env python3
"""Compose Orator ad creative finals at exact Google App campaign sizes."""
from PIL import Image, ImageDraw, ImageFont, ImageFilter
import os

ROOT = "ad-campaign/creative"
BASE = os.path.join(ROOT, "base")
FONT_DIR = "/usr/share/fonts/truetype/dejavu"
TEAL = (40, 94, 97)
WHITE = (255, 255, 255)


def font(size, bold=True):
    name = "DejaVuSans-Bold.ttf" if bold else "DejaVuSans.ttf"
    return ImageFont.truetype(os.path.join(FONT_DIR, name), size)


def cover(img, w, h):
    iw, ih = img.size
    scale = max(w / iw, h / ih)
    img = img.resize((round(iw * scale), round(ih * scale)), Image.LANCZOS)
    x = (img.width - w) // 2
    y = (img.height - h) // 2
    return img.crop((x, y, x + w, y + h))


def text(draw, xy, s, fnt, fill=WHITE, anchor="la",
         shadow=True, spacing=None):
    x, y = xy
    if shadow:
        draw.text((x + 3, y + 3), s, font=fnt, fill=(0, 0, 0, 110),
                  anchor=anchor, spacing=spacing)
    draw.text(xy, s, font=fnt, fill=fill, anchor=anchor, spacing=spacing)


def wordmark(draw, xy, icon_img, fnt_size, text_offset=0):
    """Draw the app icon + Orator wordmark at top-left anchor."""
    x, y = xy
    size = fnt_size + 8
    icon = icon_img.resize((size, size), Image.LANCZOS)
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, size, size), radius=size // 5,
                                           fill=255)
    draw._image.paste(icon, (x, y), mask)
    text(draw, (x + size + 12 + text_offset, y + (size - fnt_size) // 2),
         "Orator", font(fnt_size), anchor="la")


def phone(draw, img, x, y, w):
    """Phone mockup with the real reader screenshot inside."""
    ratio = 0.4625
    h = round(w / ratio)
    screen = cover(img, w, h)
    frame = (x - 14, y - 14, x + w + 14, y + h + 14)
    draw.rounded_rectangle(frame, radius=34, fill=(18, 25, 27))
    draw.rounded_rectangle((x - 4, y - 4, x + w + 4, y + h + 4), radius=24,
                           fill=(255, 255, 255))
    draw._image.paste(screen, (x, y))
    draw.rounded_rectangle((x + w // 2 - 34, y - 9, x + w // 2 + 34, y + 5),
                           radius=8, fill=(18, 25, 27))


def shadow_rect(draw, box, alpha=95):
    ov = Image.new("RGBA", draw._image.size, (0, 0, 0, 0))
    ImageDraw.Draw(ov).rectangle(box, fill=(0, 0, 0, alpha))
    draw._image.alpha_composite(ov)


def rounded_icon(img, size, radius=None):
    img = img.resize((size, size), Image.LANCZOS)
    radius = radius or size // 5
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, size, size), radius=radius,
                                           fill=255)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(img, (0, 0), mask)
    return out


def main():
    icon = Image.open("playstore-docs/graphics/app-icon-512.png").convert("RGBA")
    shot = Image.open("playstore-docs/screenshots/phone/03-reader.jpg").convert("RGB")

    bg1 = Image.open(os.path.join(BASE, "01-landscape-bg.png")).convert("RGBA")
    bg2 = Image.open(os.path.join(BASE, "02-square-bg.png")).convert("RGBA")
    bg3 = Image.open(os.path.join(BASE, "03-portrait-bg.png")).convert("RGBA")
    bg4 = Image.open(os.path.join(BASE, "04-book-sound.png")).convert("RGBA")
    bg5 = Image.open(os.path.join(BASE, "05-night-reading.png")).convert("RGBA")

    # 1. Hero landscape 1200x628 — uninterrupted-book promise + phone mockup
    img = cover(bg1, 1200, 628).copy()
    d = ImageDraw.Draw(img)
    shadow_rect(d, (0, 0, 560, 628))
    text(d, (70, 165), "Reading without", font(48), anchor="la")
    text(d, (70, 225), "ad breaks", font(58), anchor="la")
    text(d, (72, 302), "Your reading stays uninterrupted", font(27), anchor="la",
         fill=(255, 255, 255, 225))
    phone(d, shot, 880, 84, 216)
    wordmark(d, (70, 528), icon, 30)
    img.convert("RGB").save(os.path.join(ROOT, "01-hero-landscape-1200x628.png"))

    # 2. Square 1200x1200 — icon block, uninterrupted-book claim
    img = cover(bg2, 1200, 1200).copy()
    d = ImageDraw.Draw(img)
    ic = rounded_icon(icon, 300)
    d._image.alpha_composite(ic, (450, 150))
    text(d, (600, 640), "No ad breaks", font(56), anchor="ma")
    text(d, (600, 740), "No pop-ups in your book.", font(30), anchor="ma",
         fill=(255, 255, 255, 225))
    wordmark(d, (600, 1010), icon, 34)
    # center the wordmark (icon+text are asymmetric) by measuring text width
    d2 = ImageDraw.Draw(img)
    t = d2.textbbox((0, 0), "Orator", font=font(34))
    tw = t[2] - t[0]
    img.save(os.path.join(ROOT, "02-icon-square-1200x1200.png"))

    # 3. Portrait 1200x1500 — reader screenshot, interruption-free claim
    sq = cover(bg3, 1200, 1200)
    img = Image.new("RGBA", (1200, 1500), TEAL + (255,))
    img.paste(sq, (0, 150))
    d = ImageDraw.Draw(img)
    text(d, (600, 100), "Read without", font(58), anchor="ma")
    text(d, (600, 175), "interruptions", font(58), anchor="ma")
    phone(d, shot, 450, 420, 300)
    text(d, (600, 1380), "Turn any document into speech", font(28), anchor="ma",
         fill=(255, 255, 255, 225))
    img.convert("RGB").save(os.path.join(ROOT, "03-reader-portrait-1200x1500.png"))

    # 4. Square 1200x1200 — open book with sound waves
    img = cover(bg4, 1200, 1200).copy()
    d = ImageDraw.Draw(img)
    text(d, (600, 860), "Your books, read aloud", font(52), anchor="ma")
    text(d, (600, 945), "EPUB  ·  PDF  ·  Markdown  ·  TXT", font(30), anchor="ma",
         fill=(255, 255, 255, 225))
    wordmark(d, (600, 1060), icon, 34)
    img.convert("RGB").save(os.path.join(ROOT, "04-books-square-1200x1200.png"))

    # 5. Landscape 1200x628 — calm night reading scene
    img = cover(bg5, 1200, 628).copy()
    d = ImageDraw.Draw(img)
    shadow_rect(d, (0, 0, 640, 628))
    text(d, (70, 180), "Peaceful reading", font(60), anchor="la")
    text(d, (72, 268), "No ad breaks. No pop-ups.", font(30),
         anchor="la", fill=(255, 255, 255, 225))
    wordmark(d, (70, 528), icon, 30)
    img.convert("RGB").save(os.path.join(ROOT, "05-night-landscape-1200x628.png"))

    print("done")


if __name__ == "__main__":
    main()
