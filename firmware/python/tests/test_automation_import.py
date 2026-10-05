"""Tests for importing the firmware automation package."""

from __future__ import annotations

import os
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


AUTOMATION_INIT = (
    Path(__file__).resolve().parents[1] / "automation" / "__init__.py"
)


class TestAutomationImport(unittest.TestCase):
    """Tests for automation package import resolution."""

    def test_import_uses_repository_tasks_from_untrusted_working_directory(
        self,
    ) -> None:
        """The process working directory must not supply the tasks module."""
        with tempfile.TemporaryDirectory() as directory:
            temp_dir = Path(directory)
            firmware_dir = temp_dir / "repository" / "firmware"
            automation_dir = firmware_dir / "python" / "automation"
            attacker_dir = temp_dir / "attacker"
            trusted_marker = temp_dir / "trusted-marker"
            attacker_marker = temp_dir / "attacker-marker"

            automation_dir.mkdir(parents=True)
            attacker_dir.mkdir()
            shutil.copy(AUTOMATION_INIT, automation_dir / "__init__.py")
            (firmware_dir / "tasks.py").write_text(
                "from pathlib import Path\n"
                f"Path({str(trusted_marker)!r}).touch()\n"
            )
            (attacker_dir / "tasks.py").write_text(
                "from pathlib import Path\n"
                f"Path({str(attacker_marker)!r}).touch()\n"
            )

            environment = os.environ.copy()
            environment["PYTHONPATH"] = str(firmware_dir / "python")
            result = subprocess.run(
                [sys.executable, "-s", "-c", "import automation"],
                cwd=attacker_dir,
                env=environment,
                capture_output=True,
                text=True,
            )

            self.assertEqual(0, result.returncode, result.stderr)
            self.assertTrue(trusted_marker.exists())
            self.assertFalse(attacker_marker.exists())


if __name__ == "__main__":
    unittest.main()
