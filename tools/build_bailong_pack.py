"""Build a companion-android character pack from a local Bailong release.

Only run this on assets you are allowed to use. The upstream repository does
not declare an open licence; the person who requested this conversion stated
that they obtained permission from the author.
"""

import argparse
import io
import json
import pathlib
import shutil
import zipfile
from PIL import Image

try:
    import ftfy
except Exception:
    ftfy = None

W, H = 480, 540
QUALITY = 82


def find_manifest(root: pathlib.Path) -> pathlib.Path:
    # ZIP filenames from the Windows release are mojibake in this environment,
    # so identify the manifest by its JSON shape instead of its Chinese name.
    for path in root.rglob("*.json"):
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
        except Exception:
            continue
        if isinstance(data, dict) and "idle" in data and ("variants" in data or "workAnimations" in data):
            return path
    raise SystemExit("asset manifest JSON not found")


def walk_items(node):
    if isinstance(node, dict):
        if "id" in node and ("file" in node or "playbackFile" in node):
            yield node
        for value in node.values():
            yield from walk_items(value)
    elif isinstance(node, list):
        for value in node:
            yield from walk_items(value)


def fix_name(value: str) -> str:
    if ftfy is None:
        return value
    try:
        return ftfy.fix_text(value)
    except Exception:
        return value


def find_asset(asset_dir: pathlib.Path, item: dict) -> pathlib.Path:
    wanted = []
    for raw in (item.get("playbackFile"), item.get("file")):
        if not raw:
            continue
        rel = str(raw).replace("\\", "/")
        wanted.append(rel)
        wanted.append(fix_name(rel))
        wanted.append(pathlib.Path(rel).name)
        wanted.append(fix_name(pathlib.Path(rel).name))
    all_webp = list(asset_dir.rglob("*.webp"))
    for path in all_webp:
        rel = str(path.relative_to(asset_dir))
        variants = {rel, path.name, fix_name(rel), fix_name(path.name)}
        if any(v in wanted for v in variants):
            return path
    # Last resort: compare basenames after normalisation.
    for path in all_webp:
        for want in wanted:
            if fix_name(path.name) == fix_name(want):
                return path
    raise FileNotFoundError(f"asset not found for {item.get('id')}: {wanted}")


def frame_rgba(image: Image.Image) -> Image.Image:
    image = image.convert("RGBA")
    scale = min(W / image.width, H / image.height)
    new_w = max(1, round(image.width * scale))
    new_h = max(1, round(image.height * scale))
    resized = image.resize((new_w, new_h), Image.Resampling.LANCZOS)
    canvas = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    canvas.alpha_composite(resized, ((W - new_w) // 2, (H - new_h) // 2))
    return canvas


def encode(src: pathlib.Path, dst: pathlib.Path) -> dict:
    with Image.open(src) as image:
        frames = []
        durations = []
        for i in range(getattr(image, "n_frames", 1)):
            image.seek(i)
            frames.append(frame_rgba(image))
            durations.append(int(image.info.get("duration", 40) or 40))
    dst.parent.mkdir(parents=True, exist_ok=True)
    frames[0].save(
        dst,
        save_all=True,
        append_images=frames[1:],
        duration=durations,
        loop=0,
        quality=QUALITY,
        method=6,
    )
    total_ms = sum(durations)
    return {"frames": len(frames), "durationMs": max(1, total_ms)}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", required=True)
    parser.add_argument("--out", required=True)
    args = parser.parse_args()

    root = pathlib.Path(args.source)
    manifest_path = find_manifest(root)
    asset_dir = manifest_path.parent
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))

    out = pathlib.Path(args.out)
    if out.exists():
        shutil.rmtree(out)
    out.mkdir(parents=True)

    clips = {}
    seen = set()
    for item in walk_items(manifest):
        key = str(item.get("id") or "").strip()
        if not key or key in seen:
            continue
        try:
            src = find_asset(asset_dir, item)
        except FileNotFoundError as error:
            print("SKIP", error)
            continue
        filename = f"clip-{len(clips):03d}.webp"
        try:
            meta = encode(src, out / filename)
        except Exception as error:
            print("FAIL", key, src, error)
            continue
        clips[key] = {
            "file": filename,
            "loop": "idle" in key.lower() or key == "idle",
            **meta,
        }
        seen.add(key)
        print("OK", key, filename, meta)

    if not clips:
        raise SystemExit("no clips converted")

    def pick(*needles):
        for key in clips:
            low = key.lower()
            if any(n.lower() in low for n in needles):
                return key
        return None

    idle = pick("idle", "待机") or next(iter(clips))
    state_source = {
        "idle": idle,
        "thinking": pick("think", "思考") or idle,
        "success": pick("success", "完成", "开心", "庆祝") or idle,
        "error": pick("error", "失败", "错") or idle,
    }
    # Add English aliases that the service uses for built-in states.
    for alias, source in state_source.items():
        if source in clips:
            clips[alias] = dict(clips[source])
            clips[alias]["loop"] = alias == "idle"

    meta = {
        "id": "bailong",
        "name": "GPT白龙娘",
        "subtitle": f"{len(clips)} 动作 · 白龙桌宠",
        "author": "jingh12312-rgb",
        "license": "Author permission for personal use; upstream licence unspecified",
        "fps": 30,
        "frameWidth": W,
        "frameHeight": H,
        "defaultClip": "idle",
        "states": {key: key for key in state_source},
        "clips": clips,
    }
    (out / "character.json").write_text(json.dumps(meta, ensure_ascii=False, indent=2), encoding="utf-8")
    (out / "NOTICE.md").write_text(
        "# GPT白龙娘角色包\n\n"
        "- 来源：https://github.com/jingh12312-rgb/bailong-desktop-pet\n"
        "- 版本：v0.4.1\n"
        "- 作者：jingh12312-rgb\n"
        "- 原仓库未指定开放许可；本包按作者许可仅用于个人桌宠，禁止公开分发或商用。\n"
        "- 动作素材由原项目 releases 中的 Windows 包转换而来，仅做尺寸/编码转换，未改角色形象。\n",
        encoding="utf-8",
    )
    # Preview from the idle clip's first frame.
    idle_path = out / clips["idle"]["file"]
    with Image.open(idle_path) as image:
        image.seek(0)
        image.convert("RGBA").save(out / "preview.png")
    print("DONE", len(clips), "clips", sum(p.stat().st_size for p in out.glob("*.webp")) / 1024 / 1024, "MB")


if __name__ == "__main__":
    main()
