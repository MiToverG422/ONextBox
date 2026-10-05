import importlib.util
import json
import subprocess
import tempfile
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location('sync_source', Path(__file__).parents[1] / 'scripts/sync_source.py')
sync = importlib.util.module_from_spec(spec)
spec.loader.exec_module(sync)


class PublicationTests(unittest.TestCase):
    def test_allowlist(self):
        for name in ('README.en.md', 'app/src/main/res/raw/video.mp4', 'gradle/wrapper/gradle-wrapper.jar', '.github/workflows/android-build.yml'):
            self.assertTrue(sync.eligible(name), name)
        for name in ('../README.md', '/README.md', 'app/src/../../.git/config', 'app/src/build/a.kt', 'app/src/debug.apk', '.github/workflows/request-source-sync.yml', '.github/workflows/sync-source.yml', 'tmp/settings.apk', '.codex_extract/Settings.apk', 'release.jks', 'local.properties'):
            self.assertFalse(sync.eligible(name), name)

    def test_secret_guard(self):
        for name, content in (
            ('app/src/key.pem', b'not public'),
            ('docs/.env', b'password=value'),
            ('docs/example.txt', b'-----BEGIN OPENSSH PRIVATE KEY-----'),
            ('docs/example.txt', b'ghp_' + b'x' * 36),
            ('docs/example.txt', b'1234567890:' + b'x' * 35),
        ):
            self.assertFalse(sync.safe_content(name, content))
        self.assertTrue(sync.safe_content('app/src/example.kt', b'val token = System.getenv("TOKEN")'))

    def test_copy_delete_idempotence_and_atomic_validation(self):
        with tempfile.TemporaryDirectory() as task_dir:
            root = Path(task_dir)
            source, destination = root / 'source', root / 'destination'
            source.mkdir()
            destination.mkdir()
            def git(*args):
                return subprocess.run(['git', '-C', str(source), *args], check=True, capture_output=True)
            git('init', '-q')
            git('config', 'user.name', 'Test')
            git('config', 'user.email', 'test@example.invalid')
            for name in ('README.md', 'LICENSE', 'app/build.gradle.kts', 'app/src/main/AndroidManifest.xml', 'docs/old.md'):
                target = source / name
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text('safe source\n', encoding='utf-8')
            git('add', '.')
            git('commit', '-qm', 'snapshot')
            sync.synchronize(source, destination)
            manifest = destination / sync.MANIFEST
            first = manifest.read_bytes()
            sync.synchronize(source, destination)
            self.assertEqual(first, manifest.read_bytes())
            reserved = destination / '.github/workflows/sync-source.yml'
            reserved.parent.mkdir(parents=True, exist_ok=True)
            reserved.write_text('preserve automation', encoding='utf-8')
            git('rm', 'docs/old.md')
            git('commit', '-qm', 'remove')
            sync.synchronize(source, destination)
            self.assertFalse((destination / 'docs/old.md').exists())
            self.assertEqual(reserved.read_text(), 'preserve automation')
            (source / 'README.md').write_text('changed\n', encoding='utf-8')
            (source / 'docs').mkdir(exist_ok=True)
            (source / 'docs/leak.txt').write_text('1234567890:' + 'x' * 35, encoding='utf-8')
            git('add', '.')
            git('commit', '-qm', 'unsafe')
            with self.assertRaises(ValueError):
                sync.synchronize(source, destination)
            self.assertEqual((destination / 'README.md').read_text(), 'safe source\n')
            self.assertFalse((destination / 'docs/leak.txt').exists())
            manifest.write_text(json.dumps(['../outside']), encoding='utf-8')
            with self.assertRaises(ValueError):
                sync.synchronize(source, destination)


if __name__ == '__main__':
    unittest.main()
