#!/usr/bin/env python3
"""Build with the persistent local key, never printing credentials.

Back up ~/.local/share/stille-signing securely. This script deliberately never
creates or replaces a key: losing it breaks update compatibility.
"""
import os
from pathlib import Path
import subprocess
import sys


def main():
    root = Path.home() / '.local/share/stille-signing'
    key = root / 'release.p12'
    password = root / 'release.password'
    if not key.is_file() or not password.is_file():
        sys.exit('Persistent signing key is missing; restore the secure backup, not a new key.')
    for path in (root, key, password):
        if path.is_symlink() or path.stat().st_mode & 0o077:
            sys.exit('Signing directory and files must be private (directory 700, files 600).')
    env = os.environ.copy()
    env.update(
        STILLE_KEYSTORE_PATH=str(key),
        STILLE_STORE_PASSWORD=password.read_text(),
        STILLE_KEY_PASSWORD=password.read_text(),
        STILLE_KEY_ALIAS='stille-release',
    )
    repo = Path(__file__).resolve().parent.parent
    # Avoid persisting signing credentials in Gradle's configuration cache.
    return subprocess.call([
        str(repo / 'gradlew'), ':app:assembleRelease', *sys.argv[1:],
        '-Dorg.gradle.jvmargs=-Xmx1536m -Dfile.encoding=UTF-8',
        '-Pkotlin.compiler.execution.strategy=in-process',
        '--no-daemon', '--no-configuration-cache', '--max-workers=1',
    ], cwd=repo, env=env)


if __name__ == '__main__':
    sys.exit(main())
