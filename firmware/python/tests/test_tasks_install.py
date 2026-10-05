"""Tests for dependency installation tasks."""

import pathlib
import tempfile
import unittest
import unittest.mock

from tasks import install as task_install


class TestInstallTasks(unittest.TestCase):
    """Tests for invoke dependency installation tasks."""

    def test_sha256_verification_accepts_matching_file(self) -> None:
        with tempfile.NamedTemporaryFile() as file:
            file.write(b"hello")
            file.flush()

            task_install._verify_sha256(
                file.name,
                "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
            )

    def test_sha256_verification_rejects_mismatched_file(self) -> None:
        with tempfile.NamedTemporaryFile() as file:
            file.write(b"hello")
            file.flush()

            with self.assertRaisesRegex(RuntimeError, "SHA-256 mismatch"):
                task_install._verify_sha256(file.name, "0" * 64)

    def test_svd_verifies_downloaded_pack(self) -> None:
        context = unittest.mock.Mock()

        with tempfile.TemporaryDirectory() as directory:
            with (
                unittest.mock.patch.object(
                    task_install, "CONFIG_DIR", pathlib.Path(directory)
                ),
                unittest.mock.patch.object(task_install, "_download"),
                unittest.mock.patch.object(task_install, "_verify_sha256") as verify,
                unittest.mock.patch.object(task_install, "ZipFile") as zip_file,
            ):
                zip_file.return_value.__enter__.return_value.filelist = []
                task_install.svd.body(context)

        verify.assert_called_once()
        self.assertEqual(
            verify.call_args.args[1],
            "348b6fe22b6645193da283f2887a809f9f1d34b556a0d94a7a4a4a5561bd2b38",
        )

    def test_criterion_uses_hermit_on_linux(self) -> None:
        context = unittest.mock.Mock()

        with unittest.mock.patch.object(task_install.sys, "platform", "linux"):
            task_install._install_criterion(context)

        context.run.assert_called_once_with("bin/criterion-root")

    def test_criterion_uses_homebrew_on_macos(self) -> None:
        context = unittest.mock.Mock()

        with unittest.mock.patch.object(task_install.sys, "platform", "darwin"):
            task_install._install_criterion(context)

        context.run.assert_called_once_with("brew install criterion")


if __name__ == "__main__":
    unittest.main()
