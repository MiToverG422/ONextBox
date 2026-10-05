"""Publish a filtered source snapshot, without source history or metadata."""

import argparse
import json
import re
import shutil
import subprocess
from pathlib import Path, PurePosixPath

ROOT_FILES = {
    '.gitattributes', '.gitignore', 'LICENSE', 'NOTICE', 'README.md',
    'build.gradle.kts', 'settings.gradle.kts', 'gradle.properties',
    'gradlew', 'gradlew.bat', 'app/.gitignore', 'app/build.gradle.kts',
    'app/proguard-rules.pro', '.github/workflows/android-build.yml',
}
SOURCE_PREFIXES = ('app/src/', 'docs/', 'gradle/')
MANIFEST = '.github/source-sync-files.json'
EXCLUDED_PARTS = {
    '.git', '.gradle', '.idea', '.ci-signing', 'build', 'dist', 'tmp',
    'backup', '_external', 'node_modules', '__pycache__',
}
EXCLUDED_SUFFIXES = {'.apk', '.aab', '.apks', '.dex', '.log', '.bak', '.tmp', '.swp'}
SENSITIVE_SUFFIXES = {'.jks', '.keystore', '.pem', '.key', '.p12', '.pfx'}
SECRET_PATTERNS = (
    re.compile(rb'-----BEGIN (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----'),
    re.compile(rb'\bgh[pousr]_[A-Za-z0-9]{30,}\b'),
    re.compile(rb'\bgithub_pat_[A-Za-z0-9_]{40,}\b'),
    re.compile(rb'\b[0-9]{8,12}:[A-Za-z0-9_-]{35}\b'),
)


def eligible(name):
    path = PurePosixPath(name)
    if path.is_absolute() or '..' in path.parts or '\\' in name or not path.parts:
        return False
    if any(part.lower() in EXCLUDED_PARTS or part.lower().startswith('.codex') for part in path.parts):
        return False
    if path.suffix.lower() in EXCLUDED_SUFFIXES:
        return False
    return name in ROOT_FILES or (
        len(path.parts) == 1 and re.fullmatch(r'README\.[A-Za-z0-9-]+\.md', name) is not None
    ) or name.startswith(SOURCE_PREFIXES)


def safe_content(name, content):
    path = PurePosixPath(name)
    lower = path.name.lower()
    if path.suffix.lower() in SENSITIVE_SUFFIXES or lower == 'local.properties':
        return False
    if lower == '.env' or lower.startswith('.env.') or re.search(r'(?:secret|signing).*\.properties$', lower):
        return False
    if any(pattern.search(content) for pattern in SECRET_PATTERNS):
        return False
    if path.suffix.lower() in {'.mp4', '.mov', '.webm', '.png', '.jpg', '.jpeg', '.webp'}:
        # Embedded media metadata must not expose local creator/user paths.
        if re.search(rb'(?:[A-Z]:[\\/]+Users[\\/]|/Users/)', content, re.I):
            return False
    return len(content) <= 50 * 1024 * 1024


def contained(root, name):
    target = root.joinpath(*PurePosixPath(name).parts)
    if not target.resolve().is_relative_to(root):
        raise ValueError('Unsafe publication path; review the source privately.')
    return target


def tracked_files(source):
    result = subprocess.run(
        ['git', '-C', str(source), 'ls-tree', '-r', '-z', 'HEAD'],
        check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
    )
    entries = []
    for record in result.stdout.split(b'\0'):
        if not record:
            continue
        meta, name = record.split(b'\t', 1)
        mode, kind, _ = meta.decode('ascii').split()
        name = name.decode('utf-8')
        if eligible(name):
            if kind != 'blob' or mode not in {'100644', '100755'}:
                raise ValueError('Links and submodules cannot be published automatically.')
            entries.append((name, mode))
    return entries


def synchronize(source, destination):
    source, destination = source.resolve(), destination.resolve()
    if source == destination or source.is_relative_to(destination) or destination.is_relative_to(source):
        raise ValueError('Source and destination must be separate checkouts.')
    manifest_path = contained(destination, MANIFEST)
    previous = json.loads(manifest_path.read_text(encoding='utf-8')) if manifest_path.exists() else []
    if not isinstance(previous, list) or any(not isinstance(name, str) or not eligible(name) for name in previous):
        raise ValueError('Invalid publication manifest.')

    # Validate the complete snapshot before copying or deleting anything.
    pending = []
    for name, mode in tracked_files(source):
        src, dst = contained(source, name), contained(destination, name)
        if src.is_symlink() or dst.is_symlink() or not safe_content(name, src.read_bytes()):
            raise ValueError('Unsafe content detected; review the source repository privately.')
        pending.append((name, mode, src, dst))
    names = sorted(item[0] for item in pending)
    if not {'README.md', 'LICENSE', 'app/build.gradle.kts', 'app/src/main/AndroidManifest.xml'}.issubset(names):
        raise ValueError('Source snapshot is incomplete; refusing to publish.')
    removals = [contained(destination, name) for name in set(previous) - set(names)]
    if any(path.is_symlink() or (path.exists() and not path.is_file()) for path in removals):
        raise ValueError('Unsafe stale publication path.')

    for _, mode, src, dst in pending:
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(src, dst)
        dst.chmod(0o755 if mode == '100755' else 0o644)
    for path in removals:
        path.unlink(missing_ok=True)
    manifest_path.parent.mkdir(parents=True, exist_ok=True)
    manifest_path.write_text(json.dumps(names, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(f'Published {len(names)} eligible source files; removed {len(removals)} stale files.')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--source', required=True, type=Path)
    parser.add_argument('--destination', required=True, type=Path)
    args = parser.parse_args()
    try:
        synchronize(args.source, args.destination)
    except (ValueError, OSError, subprocess.CalledProcessError):
        # Do not print private repository URLs, credentials, paths or file contents.
        raise SystemExit('Source sync refused. Review source files and configuration privately.')


if __name__ == '__main__':
    main()
