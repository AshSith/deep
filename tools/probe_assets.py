import json
import sys
from pathlib import Path

from PIL import Image

ASSETS = Path(r"C:\Users\huang\.dsh\profiles\desktop\node_modules\dsh-dafeiyu\assets")
PET = ASSETS / "pet"
MANIFEST = json.loads((ASSETS / "pet-manifest.json").read_text(encoding="utf-8"))
OUT = Path(__file__).resolve().parent / "_probe"
OUT.mkdir(exist_ok=True)

print("clips:")
for name, clip in MANIFEST["clips"].items():
    keys = ",".join(sorted(clip.keys()))
    print(f"  {name:18} frames={len(clip['frames']):4}  keys={keys}")
print("top-level keys:", ",".join(sorted(MANIFEST.keys())))
if "stateMap" in MANIFEST:
    print("stateMap:", MANIFEST["stateMap"])
if "idleMicroClips" in MANIFEST:
    print("idleMicroClips:", MANIFEST["idleMicroClips"])


def build(name, height, stride, quality):
    clip = MANIFEST["clips"][name]
    files = clip["frames"][::stride]
    frames = []
    for rel in files:
        img = Image.open(PET / rel).convert("RGBA")
        w = round(img.width * height / img.height)
        frames.append(img.resize((w, height), Image.LANCZOS))
    out = OUT / f"{name}_{height}_{stride}_{quality}.webp"
    frames[0].save(
        out,
        save_all=True,
        append_images=frames[1:],
        duration=round(1000 * stride / 24),
        loop=0,
        lossless=False,
        quality=quality,
        method=4,
    )
    size = out.stat().st_size
    print(f"  {out.name:32} frames={len(frames):4} {size/1024/1024:6.2f} MB  {frames[0].size}")
    return size


print("\nidle variants:")
for height, stride, quality in [(344, 1, 70), (344, 2, 70), (300, 2, 70), (240, 2, 70), (240, 2, 60), (200, 2, 70)]:
    build("idle", height, stride, quality)

print("\nall clips at 240px/stride2/q70:")
total = 0
for name in MANIFEST["clips"]:
    total += build(name, 240, 2, 70)
print(f"total: {total/1024/1024:.1f} MB")

# verify animation + alpha
probe = OUT / "idle_240_2_70.webp"
im = Image.open(probe)
print("\nprobe:", im.format, im.size, "frames:", getattr(im, "n_frames", 1), "mode:", im.mode)
print("alpha bbox:", im.convert("RGBA").getchannel("A").getbbox())
