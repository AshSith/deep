"""Convert the MIT-licensed shenshen webm clips into a WebP character pack."""

import base64
import json
import os
import pathlib
import shutil
import subprocess

ROOT = pathlib.Path(__file__).resolve().parents[1]
SRC = ROOT / "tools" / "shenshen-webm"
MANIFEST = json.loads((ROOT / "tools" / "shenshen-manifest.json").read_text(encoding="utf-8"))
OUT = pathlib.Path(os.environ.get(
    "SHENSHEN_OUT",
    str(ROOT / "app" / "src" / "main" / "assets" / "characters" / "shenshen"),
))
_LOCAL_FFMPEG = ROOT / ".tools" / "ffmpeg" / "ffmpeg-9.0.2-essentials_build" / "bin" / "ffmpeg.exe"
_LOCAL_FFPROBE = ROOT / ".tools" / "ffmpeg" / "ffmpeg-9.0.2-essentials_build" / "bin" / "ffprobe.exe"
FFMPEG = pathlib.Path(os.environ.get("FFMPEG", str(_LOCAL_FFMPEG) if _LOCAL_FFMPEG.exists() else "ffmpeg"))
FFPROBE = pathlib.Path(os.environ.get("FFPROBE", str(_LOCAL_FFPROBE) if _LOCAL_FFPROBE.exists() else "ffprobe"))

FPS = int(os.environ.get("SHENSHEN_FPS", "18"))
W = int(os.environ.get("SHENSHEN_W", "320"))
H = int(os.environ.get("SHENSHEN_H", "360"))
CROP = os.environ.get("SHENSHEN_CROP", "320:360:160:0")
QUALITY = int(os.environ.get("SHENSHEN_QUALITY", "15"))
EXTRA_FILTER = os.environ.get("SHENSHEN_EXTRA_FILTER", "")
VIDEO_FILTER = f"fps={FPS},crop={CROP},scale={W}:{H}"
if EXTRA_FILTER:
    VIDEO_FILTER += "," + EXTRA_FILTER

ALIASES = {
    "待机呼吸休闲": "idle",
    "深度思考碎碎念": "thinking",
    "工作状态-雀跃庆祝": "success",
    "工作状态-垂头叹气冒汗": "error",
    "被鼠标拖拽悬空反馈": "dragging",
    "点击回应-开心跃动": "dragging_release",
}
LOOPS = {"idle", "dragging"}


def duration(file: pathlib.Path) -> float:
    proc = subprocess.run(
        [str(FFPROBE), "-v", "error", "-show_entries", "format=duration", "-of", "default=nw=1:nk=1", str(file)],
        capture_output=True,
        text=True,
    )
    try:
        return float(proc.stdout.strip())
    except Exception:
        return 10.0


def prepare_sources():
    """CI can clone the upstream repo instead of committing 52 MB of webm."""
    if any(SRC.glob("*.webm")):
        return
    repo = pathlib.Path(os.environ.get("SHENSHEN_SOURCE", ""))
    if not repo.is_dir():
        raise SystemExit("no shenshen webm and SHENSHEN_SOURCE is not set")
    SRC.mkdir(parents=True, exist_ok=True)
    for video in repo.rglob("*.webm"):
        stem = base64.urlsafe_b64encode(video.stem.encode("utf-8")).decode("ascii").rstrip("=")
        shutil.copyfile(video, SRC / f"{stem}.webm")


def main():
    prepare_sources()
    if OUT.exists():
        shutil.rmtree(OUT)
    OUT.mkdir(parents=True)

    clips = {}
    used_aliases = set()
    for i, item in enumerate(MANIFEST):
        source = SRC / item["file"]
        if not source.is_file():
            print("MISSING", source)
            continue
        name = item["name"]
        key = ALIASES.get(name, name)
        if name in ALIASES:
            used_aliases.add(name)
        file_name = f"clip-{i:03d}.webp"
        dest = OUT / file_name
        proc = subprocess.run(
            [
                str(FFMPEG), "-y", "-hide_banner", "-loglevel", "error",
                "-c:v", "libvpx-vp9", "-i", str(source),
                "-vf", VIDEO_FILTER,
                "-c:v", "libwebp_anim", "-quality", str(QUALITY), "-loop", "0", "-an",
                str(dest),
            ],
            capture_output=True,
            text=True,
        )
        if proc.returncode != 0 or not dest.is_file():
            print("FAIL", name, proc.stderr[-300:])
            continue
        secs = duration(source)
        clips[key] = {
            "file": file_name,
            "loop": key in LOOPS,
            "frames": max(1, round(secs * FPS)),
            "durationMs": round(secs * 1000),
        }
        print(f"{i+1:3}/{len(MANIFEST)} {key} {dest.stat().st_size/1024/1024:.2f}MB")

    # Aliases point at the same file as their source clip.
    for source_name, alias in ALIASES.items():
        if source_name not in used_aliases:
            continue
        # find by source file name from manifest
        for item in MANIFEST:
            if item["name"] == source_name:
                # Locate the converted clip by its file name on disk.
                for key, value in clips.items():
                    if value["file"].endswith(f"{MANIFEST.index(item):03d}.webp"):
                        clips[alias] = dict(value)
                        clips[alias]["loop"] = alias in LOOPS
                        break
                break

    # Ensure state aliases exist even if a source name shifts.
    for alias in ("idle", "thinking", "success", "error", "dragging", "dragging_release"):
        if alias not in clips and clips:
            fallback = next(iter(clips.values()))
            clips[alias] = dict(fallback)
            clips[alias]["loop"] = alias in LOOPS

    meta = {
        "id": "shenshen",
        "name": "神神",
        "subtitle": "女仆 · 106 动作",
        "author": "MerZlin / dsh-pet-indesktop",
        "license": "MIT; see NOTICE.md",
        "fps": FPS,
        "frameWidth": W,
        "frameHeight": H,
        "defaultClip": "idle",
        "states": {
            "idle": "idle",
            "thinking": "thinking",
            "success": "success",
            "error": "error",
        },
        "clips": clips,
    }
    (OUT / "character.json").write_text(json.dumps(meta, ensure_ascii=False, indent=2), encoding="utf-8")
    (OUT / "NOTICE.md").write_text(
        """# 神神（shenshen）角色包

- 动作素材来自：https://github.com/MerZlin/dsh-pet-indesktop
- 作者：MerZlin
- 仅个人使用，不商用；按原仓库许可分发。
- 动作由原仓库 webm 转为透明 WebP，未修改角色形象。
""",
        encoding="utf-8",
    )
    # Preview from the idle clip's first frame.
    if "idle" in clips:
        preview = OUT / "preview.png"
        subprocess.run(
            [str(FFMPEG), "-y", "-hide_banner", "-loglevel", "error", "-i", str(OUT / clips["idle"]["file"]), "-frames:v", "1", "-update", "1", str(preview)],
            capture_output=True,
        )
    print("DONE", len(clips), "clips", sum(f.stat().st_size for f in OUT.glob("*.webp")) / 1024 / 1024, "MB")


if __name__ == "__main__":
    main()
