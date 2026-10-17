"""Run with python3 -m unittest discover -s tools -p 'test_signed_release.py'."""
import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('signed_release', Path(__file__).with_name('build-signed-release.py'))
helper = importlib.util.module_from_spec(spec)
spec.loader.exec_module(helper)


class SignedReleaseTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.home = Path(self.tmp.name)
        self.home_patch = patch.object(helper.Path, 'home', return_value=self.home)
        self.home_patch.start()
        self.addCleanup(self.home_patch.stop)

    def make_key(self):
        root = self.home / '.local/share/stille-signing'
        root.mkdir(parents=True, mode=0o700)
        for name, value in [('release.p12', 'test-placeholder'), ('release.password', 'test-password')]:
            p = root / name
            p.write_text(value)
            p.chmod(0o600)
        return root

    def test_missing_key_is_not_regenerated(self):
        with patch.object(helper.subprocess, 'call') as run, self.assertRaises(SystemExit):
            helper.main()
        run.assert_not_called()
        self.assertFalse((self.home / '.local/share/stille-signing').exists())

    def test_public_file_permissions_are_rejected(self):
        root = self.make_key()
        (root / 'release.password').chmod(0o644)
        with patch.object(helper.subprocess, 'call') as run, self.assertRaises(SystemExit):
            helper.main()
        run.assert_not_called()

    def test_symlink_is_rejected(self):
        root = self.make_key()
        target = self.home / 'external-password'
        target.write_text('test-password')
        target.chmod(0o600)
        # Only replacing a known test fixture within this TemporaryDirectory.
        path = root / 'release.password'
        path.unlink()
        path.symlink_to(target)
        with patch.object(helper.subprocess, 'call') as run, self.assertRaises(SystemExit):
            helper.main()
        run.assert_not_called()

    def test_credentials_stay_out_of_arguments(self):
        self.make_key()
        with patch.object(helper.sys, 'argv', ['helper', '--offline']), patch.object(helper.subprocess, 'call', return_value=0) as run:
            self.assertEqual(helper.main(), 0)
        args = run.call_args.args[0]
        env = run.call_args.kwargs['env']
        self.assertNotIn('test-password', ' '.join(args))
        self.assertEqual(env['STILLE_STORE_PASSWORD'], 'test-password')
        self.assertEqual(env['STILLE_KEY_ALIAS'], 'stille-release')
        self.assertIn('--offline', args)
        self.assertIn('-Dorg.gradle.jvmargs=-Xmx1536m -Dfile.encoding=UTF-8', args)
        self.assertIn('-Pkotlin.compiler.execution.strategy=in-process', args)
        self.assertEqual(args[-3:], ['--no-daemon', '--no-configuration-cache', '--max-workers=1'])


if __name__ == '__main__':
    unittest.main()
