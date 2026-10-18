import json
from pathlib import Path

from PIL import Image

ASSETS = Path(r"C:\Users\huang\.dsh\profiles\desktop\node_modules\dsh-dafeiyu\assets")
PET = ASSETS / "pet"
MANIFEST = json.loads((ASSETS / "pet-manifest.json").read_text(encoding="utf-8"))
OUT = Path(__file__).resolve().parent / "_probe"
OUT.mkdir(exist_ok=True)

for name in ("idle", "thinking", "poke"):
    clip = MANIFEST["clips"][name]
    print(name, "frameMs:", clip.get("frameMs"), "loop:", clip.get("loop"), "source:", clip.get("source"))

clip = MANIFEST["clips"]["idle"]
prev = None
dups = 0
diffs = []
for rel in clip["frames"]:
    im = Image.open(PET / rel).convert("RGBA")
    if prev is not None:
        # mean abs difference on a small grayscale version
        a = prev.resize((64, 54)).convert("L")
        b = im.resize((64, 54)).convert("L")
        import numpy as np

        d = float(np.abs(np.asarray(a, dtype=np.int16) - np.asarray(b, dtype=np.int16)).mean())
        diffs.append(d)
        if d < 0.01:
            dups += 1
    prev = im
print("idle exact-ish duplicates:", dups, "of", len(clip["frames"]))
print("diff mean/median/max:", sum(diffs) / len(diffs), sorted(diffs)[len(diffs) // 2], max(diffs))

# contact sheet of 8 evenly spaced frames, on a checker background
picks = [clip["frames"][i] for i in range(0, len(clip["frames"]), len(clip["frames"]) // 8)][:8]
cell_w, cell_h = 206, 172
sheet = Image.new("RGBA", (cell_w * 4, cell_h * 2), (30, 30, 40, 255))
for idx, rel in enumerate(picks):
    im = Image.open(PET / rel).convert("RGBA").resize((cell_w, cell_h), Image.LANCZOS)
    x = (idx % 4) * cell_w
    y = (idx // 4) * cell_h
    sheet.alpha_composite(im, (x, y))
sheet.save(OUT / "idle_contact.png")
print("contact sheet:", OUT / "idle_contact.png")
