"""Convert hunmer/aboutme-pets spritesheets into companion-android packs.

The upstream store ships one 640x360-per-cell spritesheet per action. This
script rebuilds those cells into animated WebP clips, keeping every source
frame (12fps) and the 640x360 canvas. No frame dropping and no downscaling.
"""

import argparse
import json
import pathlib
import shutil
import zipfile
from multiprocessing import Pool, cpu_count
from PIL import Image

PETS = {
    "dsh": {
        "name": "蓝毛小女仆",
        "idle": "待机呼吸休闲",
        "author": "hunmer / PC2005-cloud/dsh-pet",
        "license": "素材禁止商用；二创在公开渠道须署名 PC2005-cloud/dsh-pet",
    },
    "dsh1": {
        "name": "蓝毛小女仆·新动作包",
        "idle": "站着晃神打瞌睡",
        "author": "hunmer / PC2005-cloud/dsh-pet",
        "license": "素材禁止商用；二创在公开渠道须署名 PC2005-cloud/dsh-pet",
    },
    "gpt": {
        "name": "GPT",
        "idle": "待机呼吸起伏",
        "author": "hunmer / PC2005-cloud/dsh-pet",
        "license": "素材禁止商用；二创在公开渠道须署名 PC2005-cloud/dsh-pet",
    },
    "claude": {
        "name": "Claude",
        "idle": "待机呼吸休闲",
        "author": "hunmer / PC2005-cloud/dsh-pet",
        "license": "素材禁止商用；二创在公开渠道须署名 PC2005-cloud/dsh-pet",
    },
}

QUALITY = 90


def animation_paths(pack_dir: pathlib.Path):
    actions = json.loads((pack_dir / "actions.json").read_text(encoding="utf-8"))
    return actions.get("animations", [])


def find_alias(animations, *needles):
    for anim in animations:
        text = f"{anim.get('id', '')} {anim.get('name', '')} {anim.get('category', '')}"
        if any(n in text for n in needles):
            return anim.get("id")
    return None


def process_one(job):
    source, dest, action = job
    sheet = source / action["sources"]["webp"]
    meta = action.get("spritesheet", {})
    cols = int(meta.get("columns") or 8)
    fw = int(meta.get("frameWidth") or 640)
    fh = int(meta.get("frameHeight") or 360)
    count = int(meta.get("frameCount") or 120)
    fps = int(meta.get("fps") or 12)
    try:
        with Image.open(sheet) as image:
            image = image.convert("RGBA")
            if image.width < cols * fw or image.height < fh:
                return action["id"], None, f"sheet too small {image.size}"
            frames = []
            for i in range(count):
                x = (i % cols) * fw
                y = (i // cols) * fh
                if y + fh > image.height:
                    break
                frames.append(image.crop((x, y, x + fw, y + fh)))
        if not frames:
            return action["id"], None, "no frames"
        filename = dest.name + ".webp"
        outfile = dest.with_suffix(".webp")
        duration = max(1, round(1000 / max(1, fps)))
        frames[0].save(
            outfile,
            format="WEBP",
            save_all=True,
            append_images=frames[1:],
            duration=duration,
            loop=0,
            quality=QUALITY,
            method=4,
        )
        return action["id"], {
            "file": filename,
            "loop": False,
            "frames": len(frames),
            "durationMs": len(frames) * duration,
        }, None
    except Exception as e:
        return action["id"], None, str(e)


def build_pack(source: pathlib.Path, out: pathlib.Path, key: str, jobs: int):
    info = PETS[key]
    pack_dir = source / "pets" / key
    animations = animation_paths(pack_dir)
    if not animations:
        raise SystemExit(f"{key}: no actions")
    target = out / key
    if target.exists():
        shutil.rmtree(target)
    target.mkdir(parents=True)

    work = []
    for index, action in enumerate(animations):
        work.append((pack_dir, target / f"clip-{index:03d}", action))

    clips = {}
    with Pool(jobs) as pool:
        for i, (aid, meta, error) in enumerate(pool.imap_unordered(process_one, work), 1):
            if meta is None:
                print(f"{key}: FAIL {aid}: {error}", flush=True)
                continue
            clips[aid] = meta
            print(f"{key}: {i}/{len(work)} {aid} {meta['frames']}f", flush=True)

    if not clips:
        raise SystemExit(f"{key}: no clips")

    idle = info["idle"] if info["idle"] in clips else find_alias(animations, "待机", "idle")
    idle = idle if idle in clips else next(iter(clips))
    aliases = {
        "idle": idle,
        "thinking": find_alias(animations, "深度思考", "思考"),
        "success": find_alias(animations, "庆祝", "开心", "雀跃"),
        "error": find_alias(animations, "垂头", "叹气", "失败"),
        "dragging": find_alias(animations, "拖拽"),
    }
    for alias, source_id in aliases.items():
        if source_id in clips:
            clips[alias] = dict(clips[source_id])
            clips[alias]["loop"] = alias == "idle"
    if "idle" in clips:
        clips["idle"]["loop"] = True

    char = {
        "id": key,
        "name": info["name"],
        "subtitle": f"{len(animations)} 动作 · 640×360",
        "author": info["author"],
        "license": info["license"],
        "fps": 12,
        "frameWidth": 640,
        "frameHeight": 360,
        "defaultClip": "idle",
        "states": {k: k for k in aliases if k in clips},
        "clips": clips,
    }
    (target / "character.json").write_text(
        json.dumps(char, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    (target / "NOTICE.md").write_text(
        f"""# {info['name']} 角色包

- 来源仓库：https://github.com/hunmer/aboutme-pets
- 上游动画：https://github.com/PC2005-cloud/dsh-pet
- 上游固定提交：b78e3fcf92ac78cee569aed7cc27eeb2a8d51757
- 作者：hunmer / PC2005-cloud/dsh-pet
- 素材许可：素材禁止商用；二创在公开渠道须署名上游仓库。
- 转换：仅将上游 640×360 精灵图切回逐帧 WebP，保留全部帧与 12fps，未降帧、未缩放。
""",
        encoding="utf-8",
    )
    # First idle frame as preview.
    idle_file = target / clips["idle"]["file"]
    with Image.open(idle_file) as image:
        image.seek(0)
        image.convert("RGBA").save(target / "preview.png")

    zip_path = out / f"{key}-character.zip"
    if zip_path.exists():
        zip_path.unlink()
    with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED, compresslevel=6) as z:
        for path in sorted(target.rglob("*")):
            if path.is_file():
                z.write(path, pathlib.Path(key) / path.relative_to(target))
    print(f"{key}: DONE {len(clips)} clips, {zip_path.stat().st_size / 1024 / 1024:.1f} MB", flush=True)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", required=True)
    parser.add_argument("--out", required=True)
    parser.add_argument("--jobs", type=int, default=4)
    args = parser.parse_args()
    out = pathlib.Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    for key in PETS:
        build_pack(pathlib.Path(args.source), out, key, args.jobs)


if __name__ == "__main__":
    main()
