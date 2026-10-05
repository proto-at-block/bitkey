import tempfile
import unittest
import zipfile
from pathlib import Path

from bitkey.secure_archive import make_zip_archive


class MakeZipArchiveTest(unittest.TestCase):
    def setUp(self):
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary_directory.cleanup)
        self.parent = Path(self.temporary_directory.name)
        self.source_dir = self.parent / "bundle"
        self.source_dir.mkdir()
        (self.source_dir / "payload.bin").write_bytes(b"firmware")
        self.archive_path = self.parent / "bundle.zip"

    def test_creates_archive(self):
        result = make_zip_archive(self.source_dir)

        self.assertEqual(result, self.archive_path)
        with zipfile.ZipFile(result) as archive:
            self.assertEqual(archive.read("payload.bin"), b"firmware")

    def assert_archive_contains(self, expected: bytes):
        with zipfile.ZipFile(self.archive_path) as archive:
            self.assertEqual(archive.read("payload.bin"), expected)

    def test_replaces_existing_file(self):
        self.archive_path.write_bytes(b"existing archive")

        make_zip_archive(self.source_dir)

        self.assert_archive_contains(b"firmware")

    def test_replaces_symlink_without_overwriting_target(self):
        target = self.parent / "marker"
        target.write_bytes(b"do not overwrite")
        self.archive_path.symlink_to(target)

        make_zip_archive(self.source_dir)

        self.assertFalse(self.archive_path.is_symlink())
        self.assertEqual(target.read_bytes(), b"do not overwrite")
        self.assert_archive_contains(b"firmware")

    def test_replaces_dangling_symlink(self):
        missing_target = self.parent / "missing"
        self.archive_path.symlink_to(missing_target)

        make_zip_archive(self.source_dir)

        self.assertFalse(self.archive_path.is_symlink())
        self.assertFalse(missing_target.exists())
        self.assert_archive_contains(b"firmware")

    def test_replaces_archive_when_generated_again(self):
        make_zip_archive(self.source_dir)
        (self.source_dir / "payload.bin").write_bytes(b"updated firmware")

        make_zip_archive(self.source_dir)

        self.assert_archive_contains(b"updated firmware")


if __name__ == "__main__":
    unittest.main()
