"""Build the built-in dafeiyu character pack for the Android app.

Source frames: dsh-dafeiyu (MIT, from PC2005-cloud/dsh-pet).
Output: one animated WebP per clip, cropped to the union alpha bbox.
"""

import json
import shutil
from pathlib import Path

from PIL import Image, ImageDraw

ASSETS = Path(r"C:\Users\huang\.dsh\profiles\desktop\node_modules\dsh-dafeiyu\assets")
PET = ASSETS / "pet"
MANIFEST = json.loads((ASSETS / "pet-manifest.json").read_text(encoding="utf-8"))
ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "app" / "src" / "main" / "assets" / "characters" / "dafeiyu"
ICON_OUT = ROOT / "app" / "src" / "main" / "res"

CLIPS = {
    "idle": ("idle", True),
    "thinking": ("thinking", True),
    "success": ("success", False),
    "error": ("error", False),
    "poke": ("poke", False),
    "head_pat": ("head_pat", False),
    "tail": ("tail", False),
    "eat_token": ("eat_token", False),
    "dragging": ("dragging", True),
    "dragging_release": ("dragging_release", False),
}

HEIGHT = 344
QUALITY = 68
FRAME_MS = 42


def alpha_bbox(path: Path, threshold: int = 8):
    im = Image.open(path).convert("RGBA")
    alpha = im.getchannel("A").point(lambda v: 255 if v > threshold else 0)
    return alpha.getbbox()


def union_bbox():
    boxes = []
    for name, clip in MANIFEST["clips"].items():
        frames = clip["frames"]
        for rel in frames[::3]:
            box = alpha_bbox(PET / rel)
            if box:
                boxes.append(box)
    left = min(b[0] for b in boxes)
    top = min(b[1] for b in boxes)
    right = max(b[2] for b in boxes)
    bottom = max(b[3] for b in boxes)
    pad = 6
    left = max(0, left - pad)
    top = max(0, top - pad)
    right = min(MANIFEST["maxFrameWidth"], right + pad)
    bottom = min(MANIFEST["maxFrameHeight"], bottom + pad)
    return left, top, right, bottom


def encode_clip(name: str, crop, loop: bool):
    source = MANIFEST["clips"][name]["frames"]
    frames = []
    for rel in source:
        im = Image.open(PET / rel).convert("RGBA").crop(crop)
        if im.height != HEIGHT:
            width = round(im.width * HEIGHT / im.height)
            im = im.resize((width, HEIGHT), Image.LANCZOS)
        frames.append(im)

    out = OUT / f"{name}.webp"
    frames[0].save(
        out,
        save_all=True,
        append_images=frames[1:],
        duration=FRAME_MS,
        loop=0 if loop else 1,
        lossless=False,
        quality=QUALITY,
        method=4,
    )
    return out, len(frames), frames[0].size


def make_icons(preview: Image.Image):
    # square icon: dark rounded background + character
    size = 512
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(canvas)
    draw.rounded_rectangle((0, 0, size, size), radius=112, fill=(13, 20, 36, 255))
    # subtle glow
    glow = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    gd.ellipse((40, 250, size - 40, size + 160), fill=(90, 140, 240, 70))
    canvas.alpha_composite(glow)

    char = preview.copy()
    target_h = int(size * 0.78)
    w = round(char.width * target_h / char.height)
    char = char.resize((w, target_h), Image.LANCZOS)
    canvas.alpha_composite(char, ((size - w) // 2, size - target_h - 18))

    for density, px in {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}.items():
        folder = ICON_OUT / f"mipmap-{density}"
        folder.mkdir(parents=True, exist_ok=True)
        canvas.resize((px, px), Image.LANCZOS).save(folder / "ic_launcher.png")
        canvas.resize((px, px), Image.LANCZOS).save(folder / "ic_launcher_round.png")

    # adaptive foreground: transparent, character inside the safe zone
    fg = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    char = preview.copy()
    target_h = int(size * 0.58)
    w = round(char.width * target_h / char.height)
    char = char.resize((w, target_h), Image.LANCZOS)
    fg.alpha_composite(char, ((size - w) // 2, (size - target_h) // 2))
    for density, px in {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}.items():
        folder = ICON_OUT / f"mipmap-{density}"
        fg.resize((px, px), Image.LANCZOS).save(folder / "ic_launcher_foreground.png")


def main():
    if OUT.exists():
        shutil.rmtree(OUT)
    OUT.mkdir(parents=True)
    crop = union_bbox()
    print("union crop:", crop, "size:", (crop[2] - crop[0], crop[3] - crop[1]))

    meta = {
        "id": "dafeiyu",
        "name": "大肥鱼",
        "subtitle": "鲸尾女仆",
        "author": "PC2005-cloud / dsh-pet",
        "license": "MIT",
        "fps": 24,
        "frameWidth": 0,
        "frameHeight": HEIGHT,
        "defaultClip": "idle",
        "states": {
            "idle": "idle",
            "thinking": "thinking",
            "working": "working",
            "success": "success",
            "error": "error",
        },
        "clips": {},
    }

    total = 0
    for clip_name, (source_name, loop) in CLIPS.items():
        out, count, size = encode_clip(source_name, crop, loop)
        total += out.stat().st_size
        meta["clips"][clip_name] = {
            "file": out.name,
            "loop": loop,
            "frames": count,
            "durationMs": count * FRAME_MS,
        }
        print(f"  {clip_name:18} {count:4} frames {size[0]}x{size[1]} {out.stat().st_size/1024/1024:5.2f} MB")

    meta["frameWidth"] = round((crop[2] - crop[0]) * HEIGHT / (crop[3] - crop[1]))
    (OUT / "character.json").write_text(json.dumps(meta, ensure_ascii=False, indent=2), encoding="utf-8")

    # preview + icons
    preview = Image.open(PET / MANIFEST["clips"]["idle"]["frames"][20]).convert("RGBA").crop(crop)
    preview.save(OUT / "preview.png")
    make_icons(preview)

    # license notice
    notice = """# 素材说明 / Asset notice

角色动画来自 [PC2005-cloud/dsh-pet](https://github.com/PC2005-cloud/dsh-pet)，
经由 [dsh-dafeiyu](https://github.com/QCYTSN/dsh-dafeiyu) 转换为 WebP 帧，
以 MIT 许可证分发。原始素材版权归 PC2005-cloud 所有。

本目录中的 `*.webp` 为裁剪、缩放并重新编码后的衍生版本，仅用于本应用的
内置角色展示。完整许可见上游仓库的 LICENSE 与 ASSET_LICENSE.md。

角色名“大肥鱼”来自社区，与 DeepSeek 官方无关。
"""
    (OUT / "NOTICE.md").write_text(notice, encoding="utf-8")

    print(f"total webp: {total/1024/1024:.1f} MB")
    print("written:", OUT)


if __name__ == "__main__":
    main()
