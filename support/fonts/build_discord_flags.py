import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import urllib.request
import zipfile

from fontTools.ttLib import TTFont


COMMIT = "187e1b77ee80532eb584bcf62a2266942fa39cf9"
ROOT = Path(__file__).resolve().parents[2]
FONT = ROOT / "app/src/main/res/font/discord_twemoji_flags.ttf"
THIRD_PARTY = ROOT / "third_party/discord-twemoji"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--work-dir", type=Path, required=True)
    args = parser.parse_args()
    work = args.work_dir.resolve()
    svg_dir = work / "svg"
    svg_dir.mkdir(parents=True, exist_ok=True)
    url = f"https://codeload.github.com/discord/twemoji/zip/{COMMIT}"
    archive = zipfile.ZipFile(io.BytesIO(urllib.request.urlopen(url, timeout=90).read()))
    sources = []
    for path in sorted(archive.namelist()):
        name = path.rsplit("/", 1)[-1]
        if "/assets/svg/" not in path or not re.fullmatch(r"[0-9a-f]+-[0-9a-f]+\.svg", name):
            continue
        codepoints = [int(value, 16) for value in name[:-4].split("-")]
        if not all(0x1F1E6 <= value <= 0x1F1FF for value in codepoints):
            continue
        data = archive.read(path)
        local = "emoji_u" + "_".join(f"{value:x}" for value in codepoints) + ".svg"
        (svg_dir / local).write_bytes(data)
        sources.append({
            "country": "".join(chr(value - 0x1F1E6 + 65) for value in codepoints),
            "file": f"assets/svg/{name}",
            "sha256": hashlib.sha256(data).hexdigest(),
            "local": local,
        })
    assert len(sources) == 259, len(sources)
    env = os.environ.copy()
    env["PATH"] = str(Path(sys.executable).parent / "Scripts") + os.pathsep + env["PATH"]
    subprocess.run([
        sys.executable, "-m", "nanoemoji.nanoemoji",
        f"--build_dir={work / 'font-build'}",
        "--output_file=discord_twemoji_flags.ttf",
        "--family=ONextBox Discord Flags",
        "--color_format=glyf_colr_1",
        "--upem=1024", "--width=1275", "--ascender=950", "--descender=-250", "--linegap=0",
        *[source["local"] for source in sources],
    ], cwd=svg_dir, env=env, check=True)
    font = TTFont(work / "font-build/discord_twemoji_flags.ttf")
    assert font["COLR"].version == 1
    assert "GSUB" in font
    font["OS/2"].fsType = 0
    names = font["name"]
    names.setName("Twemoji graphics copyright Twitter, Inc. and other contributors. CC BY 4.0.", 0, 3, 1, 0x409)
    names.setName("Creative Commons Attribution 4.0 International; SVG artwork converted to a flags-only COLRv1 font.", 13, 3, 1, 0x409)
    names.setName("https://creativecommons.org/licenses/by/4.0/", 14, 3, 1, 0x409)
    FONT.parent.mkdir(parents=True, exist_ok=True)
    font.save(FONT)
    THIRD_PARTY.mkdir(parents=True, exist_ok=True)
    license_path = next(path for path in archive.namelist() if path.endswith("/LICENSE-GRAPHICS"))
    (THIRD_PARTY / "LICENSE-GRAPHICS").write_bytes(archive.read(license_path))
    (THIRD_PARTY / "NOTICE").write_text(
        "Twemoji country and region flags\n"
        "Copyright Twitter, Inc. and other contributors.\n"
        "Source: https://github.com/discord/twemoji\n"
        f"Commit: {COMMIT}\n"
        "Graphics license: Creative Commons Attribution 4.0 International\n"
        "https://creativecommons.org/licenses/by/4.0/\n"
        "The 259 regional-indicator flag SVGs were converted into a flags-only COLRv1 font.\n"
        "Artwork is unchanged; font packaging and metadata were added by ONextBox.\n",
        encoding="utf-8",
    )
    manifest = {
        "repository": "https://github.com/discord/twemoji",
        "commit": COMMIT,
        "generator": "nanoemoji 0.16.0",
        "font": str(FONT.relative_to(ROOT)).replace(os.sep, "/"),
        "sha256": hashlib.sha256(FONT.read_bytes()).hexdigest(),
        "flags": [{key: value for key, value in source.items() if key != "local"} for source in sources],
    }
    (THIRD_PARTY / "sources.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"flags": len(sources), "bytes": FONT.stat().st_size, "sha256": manifest["sha256"]}))


if __name__ == "__main__":
    main()
