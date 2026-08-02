"""Tests for the read-only git boundary."""

from __future__ import annotations

import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from feedback_loop.gitio import GitClient, GitError  # noqa: E402


def _git(repo: Path, *args: str) -> str:
    return subprocess.run(
        ["git", "-C", str(repo), *args],
        check=True,
        capture_output=True,
        text=True,
    ).stdout.strip()


class TestGitClient(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls._tmp = tempfile.TemporaryDirectory()
        cls.root = Path(cls._tmp.name)
        _git(cls.root, "init", "-q")
        _git(cls.root, "config", "user.email", "test@example.com")
        _git(cls.root, "config", "user.name", "Test")
        (cls.root / "lib.py").write_text("VALUE = 1\n", encoding="utf-8")
        (cls.root / "other.py").write_text("OTHER = 1\n", encoding="utf-8")
        _git(cls.root, "add", ".")
        _git(cls.root, "commit", "-q", "-m", "base")
        cls.base_sha = _git(cls.root, "rev-parse", "HEAD")
        (cls.root / "lib.py").write_text("VALUE = 2\n", encoding="utf-8")
        (cls.root / "other.py").write_text("OTHER = 2\n", encoding="utf-8")
        _git(cls.root, "add", ".")
        _git(cls.root, "commit", "-q", "-m", "head")
        cls.head_sha = _git(cls.root, "rev-parse", "HEAD")

    @classmethod
    def tearDownClass(cls):
        cls._tmp.cleanup()

    def test_diff_range_returns_unified_diff(self):
        client = GitClient(self.root)

        diff = client.diff_range(self.base_sha, self.head_sha, ("lib.py",))

        self.assertIn("-VALUE = 1", diff)
        self.assertIn("+VALUE = 2", diff)
        self.assertNotIn("OTHER", diff)

    def test_diff_range_without_paths_covers_all_files(self):
        client = GitClient(self.root)

        diff = client.diff_range(self.base_sha, self.head_sha)

        self.assertIn("lib.py", diff)
        self.assertIn("other.py", diff)

    def test_diff_range_truncates_with_trailer(self):
        client = GitClient(self.root)

        diff = client.diff_range(self.base_sha, self.head_sha, max_bytes=40)

        self.assertLessEqual(len(diff.encode("utf-8")), 40 + 60)
        self.assertIn("...diff truncated (", diff)

    def test_diff_range_rejects_invalid_inputs(self):
        client = GitClient(self.root)

        with self.assertRaisesRegex(GitError, "invalid commit sha"):
            client.diff_range("not-a-sha", self.head_sha)
        with self.assertRaisesRegex(GitError, "invalid commit sha"):
            client.diff_range(self.base_sha, "main")
        invalid_paths = (
            "",
            " lib.py",
            "lib.py ",
            "/tmp/lib.py",
            "../lib.py",
            "dir/../lib.py",
            "dir\\lib.py",
            "--output=/tmp/x",
            ":(glob)lib.py",
            ":/lib.py",
        )
        for path in invalid_paths:
            with self.subTest(path=path):
                with self.assertRaisesRegex(GitError, "invalid diff path"):
                    client.diff_range(self.base_sha, self.head_sha, (path,))

    def test_diff_range_unknown_sha_raises_git_error(self):
        client = GitClient(self.root)

        with self.assertRaisesRegex(GitError, "failed with exit"):
            client.diff_range("0" * 40, self.head_sha)

    def test_commit_exists(self):
        client = GitClient(self.root)

        self.assertTrue(client.commit_exists(self.head_sha))
        self.assertFalse(client.commit_exists("0" * 40))
        self.assertFalse(client.commit_exists("HEAD"))


if __name__ == "__main__":
    unittest.main()
