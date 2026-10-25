"""Build a companion-android character pack from a local Bailong release.

Only run this on assets you are allowed to use. The upstream repository does
not declare an open licence; the person who requested this conversion stated
that they obtained permission from the author.
"""

import argparse
import json
import os
import pathlib
import shutil
import subprocess
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


def encode(src: pathlib.Path, dst: pathlib.Path) -> dict:
    dst.parent.mkdir(parents=True, exist_ok=True)
    vf = (
        f"scale={W}:{H}:force_original_aspect_ratio=decrease,"
        f"pad={W}:{H}:(ow-iw)/2:(oh-ih)/2:color=black@0,format=rgba"
    )
    subprocess.run(
        [
            "ffmpeg", "-y", "-hide_banner", "-loglevel", "error",
            "-i", str(src),
            "-vf", vf,
            "-c:v", "libwebp_anim", "-quality", str(QUALITY),
            "-loop", "0", "-an", "-fps_mode", "passthrough",
            str(dst),
        ],
        check=True,
    )
    probe = subprocess.run(
        [
            "ffprobe", "-v", "error", "-select_streams", "v:0",
            "-show_entries", "stream=nb_frames,duration,avg_frame_rate",
            "-of", "json", str(dst),
        ],
        capture_output=True, text=True,
    )
    stream = {}
    try:
        stream = json.loads(probe.stdout)["streams"][0]
    except Exception:
        pass
    duration = float(stream.get("duration") or 0)
    frames = int(stream.get("nb_frames") or 0)
    if frames <= 0 and duration > 0:
        frames = max(1, round(duration * 30))
    return {"frames": frames, "durationMs": max(1, round(duration * 1000))}


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
