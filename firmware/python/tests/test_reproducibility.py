import tempfile
import unittest
from pathlib import Path

from bitkey.reproducibility import (
    EXPECTED_ARTIFACT_COUNT,
    expected_artifact_paths,
    manifest,
    write_manifest,
)


class TestReproducibilityManifest(unittest.TestCase):
    def populate_build(self, build_dir: Path) -> None:
        for relative_path in expected_artifact_paths():
            artifact = build_dir / relative_path
            artifact.parent.mkdir(parents=True, exist_ok=True)
            artifact.write_bytes(relative_path.as_posix().encode("utf-8"))

    def test_expected_artifact_set_is_complete_and_unique(self):
        artifacts = expected_artifact_paths()

        self.assertEqual(len(artifacts), EXPECTED_ARTIFACT_COUNT)
        self.assertEqual(len(set(artifacts)), EXPECTED_ARTIFACT_COUNT)
        self.assertEqual(artifacts, tuple(sorted(artifacts)))

    def test_manifest_is_independent_of_build_directory(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            short_build = Path(temp_dir) / "a"
            long_build = Path(temp_dir) / "a-much-longer-checkout-path" / "build"
            self.populate_build(short_build)
            self.populate_build(long_build)

            short_manifest = manifest(short_build)
            long_manifest = manifest(long_build)

        self.assertEqual(short_manifest, long_manifest)
        self.assertEqual(len(short_manifest.splitlines()), EXPECTED_ARTIFACT_COUNT)
        self.assertNotIn(str(short_build), short_manifest)
        self.assertNotIn(str(long_build), long_manifest)

    def test_missing_artifact_is_reported(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            build_dir = Path(temp_dir)
            self.populate_build(build_dir)
            missing = expected_artifact_paths()[0]
            (build_dir / missing).unlink()

            with self.assertRaisesRegex(FileNotFoundError, missing.as_posix()):
                manifest(build_dir)

    def test_unscoped_files_do_not_change_manifest(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            build_dir = Path(temp_dir)
            self.populate_build(build_dir)
            expected = manifest(build_dir)

            ignored = build_dir / "w1/app/w1/application/w1a-dvt-app-a-prod.map"
            ignored.write_bytes(b"path-dependent map output")
            object_file = build_dir / "w1/app/w1/application/object.o"
            object_file.write_bytes(b"intermediate object")

            self.assertEqual(manifest(build_dir), expected)

    def test_write_manifest_creates_output(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            temp_dir = Path(temp_dir)
            build_dir = temp_dir / "build"
            output = temp_dir / "manifests" / "firmware.sha256"
            self.populate_build(build_dir)

            write_manifest(build_dir, output)

            self.assertEqual(output.read_text(encoding="utf-8"), manifest(build_dir))


if __name__ == "__main__":
    unittest.main()
