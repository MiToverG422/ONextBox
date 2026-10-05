import os
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


class DirectPublicationTests(unittest.TestCase):
    def test_direct_main_publication_and_unchanged_snapshot(self):
        bash = shutil.which('bash')
        if sys.platform == 'win32':
            candidate = Path('C:/Program Files/Git/bin/bash.exe')
            bash = str(candidate) if candidate.exists() else None
        if not bash:
            self.skipTest('Bash is required for the publication integration test')
        scripts = Path(__file__).resolve().parents[1] / 'scripts'
        with tempfile.TemporaryDirectory() as task_dir:
            root = Path(task_dir)
            def git(checkout, *args):
                return subprocess.run(['git', '-C', str(checkout), *args], check=True, capture_output=True, text=True).stdout.strip()
            source_seed, public_seed = root / 'source-seed', root / 'public-seed'
            for seed in (source_seed, public_seed):
                seed.mkdir()
                git(seed, 'init', '-q', '-b', 'main')
                git(seed, 'config', 'user.name', 'Test')
                git(seed, 'config', 'user.email', 'test@example.invalid')
            for name in ('README.md', 'LICENSE', 'app/build.gradle.kts', 'app/src/main/AndroidManifest.xml'):
                target = source_seed / name
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text('snapshot one\n', encoding='utf-8')
            git(source_seed, 'add', '.')
            git(source_seed, 'commit', '-qm', 'source fixture')
            public_scripts = public_seed / '.github/scripts'
            public_scripts.mkdir(parents=True)
            for name in ('sync_source.py', 'publish_source.sh'):
                shutil.copyfile(scripts / name, public_scripts / name)
            git(public_seed, 'add', '.')
            git(public_seed, 'commit', '-qm', 'public fixture')
            source_remote, public_remote = root / 'source.git', root / 'public.git'
            subprocess.run(['git', 'clone', '--bare', str(source_seed), str(source_remote)], check=True, capture_output=True)
            subprocess.run(['git', 'clone', '--bare', str(public_seed), str(public_remote)], check=True, capture_output=True)
            source, destination = root / 'source', root / 'destination'
            for remote, checkout in ((source_remote, source), (public_remote, destination)):
                subprocess.run(['git', 'clone', str(remote), str(checkout)], check=True, capture_output=True)
            env = {**os.environ, 'PYTHON': sys.executable.replace('\\', '/')}
            def publish():
                result = subprocess.run([bash, str(destination / '.github/scripts/publish_source.sh').replace('\\', '/'), str(source).replace('\\', '/'), str(destination).replace('\\', '/')], env=env, capture_output=True, text=True)
                self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
                return result.stdout
            publish()
            self.assertEqual(git(public_remote, 'show', 'main:README.md'), 'snapshot one')
            self.assertEqual(git(public_remote, 'for-each-ref', '--format=%(refname:short)', 'refs/heads'), 'main')
            first = git(public_remote, 'rev-parse', 'main')
            self.assertIn('already up to date', publish())
            self.assertEqual(git(public_remote, 'rev-parse', 'main'), first)
            (source_seed / 'README.md').write_text('snapshot two\n', encoding='utf-8')
            git(source_seed, 'add', 'README.md')
            git(source_seed, 'commit', '-qm', 'next source fixture')
            git(source_seed, 'push', str(source_remote), 'main')
            hook = public_remote / 'hooks/pre-receive'
            hook.write_text('#!/bin/sh\nif [ ! -f "$GIT_DIR/retry-tested" ]; then\n  touch "$GIT_DIR/retry-tested"\n  exit 1\nfi\nexit 0\n', encoding='utf-8', newline='\n')
            hook.chmod(0o755)
            self.assertIn('Push attempt 1 failed', publish())
            self.assertEqual(git(public_remote, 'show', 'main:README.md'), 'snapshot two')
            self.assertEqual(git(public_remote, 'for-each-ref', '--format=%(refname:short)', 'refs/heads'), 'main')


if __name__ == '__main__':
    unittest.main()
