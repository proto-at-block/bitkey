"""Tests for the injectable repository reality boundary."""

from __future__ import annotations

import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from feedback_loop.repo_reality import CheckSummary, RepoReality  # noqa: E402


def _git(repo: Path, *args: str) -> None:
    subprocess.run(
        ["git", "-C", str(repo), *args],
        check=True,
        capture_output=True,
        text=True,
    )


class TestRepoRealityTree(unittest.TestCase):
    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.root = Path(self._tmp.name)
        self.addCleanup(self._tmp.cleanup)
        (self.root / "app" / "src").mkdir(parents=True)
        (self.root / "app" / "src" / "Main.kt").write_text("fun main() {}\n", encoding="utf-8")
        self.repo = RepoReality(self.root)

    def test_file_dir_and_parent_existence(self):
        self.assertTrue(self.repo.file_exists("app/src/Main.kt"))
        self.assertFalse(self.repo.file_exists("app/src/Missing.kt"))
        self.assertTrue(self.repo.dir_exists("app/src"))
        self.assertFalse(self.repo.dir_exists("app/missing"))
        self.assertTrue(self.repo.parent_dir_exists("app/src/New.kt"))
        self.assertFalse(self.repo.parent_dir_exists("app/missing/New.kt"))
        self.assertTrue(self.repo.parent_dir_exists("toplevel.md"))

    def test_rejects_escaping_and_absolute_paths(self):
        self.assertFalse(self.repo.file_exists("../etc/passwd"))
        self.assertFalse(self.repo.file_exists("/etc/passwd"))
        self.assertFalse(self.repo.file_exists("app\\src\\Main.kt"))
        self.assertFalse(self.repo.parent_dir_exists("../outside.txt"))
        self.assertIsNone(self.repo.read_text("../outside.txt"))

    def test_read_text(self):
        self.assertEqual(self.repo.read_text("app/src/Main.kt"), "fun main() {}\n")
        self.assertIsNone(self.repo.read_text("app/src/Missing.kt"))

    def test_existing_checks_parses_frontmatter(self):
        checks_dir = self.root / ".agents" / "checks"
        checks_dir.mkdir(parents=True)
        (checks_dir / "smoke.md").write_text(
            "---\nname: smoke\ndescription: Verifies the scaffold works.\n"
            "severity-default: low\ntools: [Read]\n---\n\n## Purpose\n",
            encoding="utf-8",
        )
        (checks_dir / "bare.md").write_text("no frontmatter\n", encoding="utf-8")

        checks = self.repo.existing_checks()

        self.assertEqual(
            checks,
            (
                CheckSummary(name="bare", description="", path=".agents/checks/bare.md"),
                CheckSummary(
                    name="smoke",
                    description="Verifies the scaffold works.",
                    path=".agents/checks/smoke.md",
                ),
            ),
        )

    def test_existing_skills_lists_directories(self):
        skills_dir = self.root / ".ai" / "skills"
        (skills_dir / "run-tests").mkdir(parents=True)
        (skills_dir / "write-docs").mkdir(parents=True)
        (skills_dir / "stray.md").write_text("not a skill dir\n", encoding="utf-8")

        self.assertEqual(self.repo.existing_skills(), ("run-tests", "write-docs"))

    def test_scoped_agents_md_path(self):
        self.assertEqual(self.repo.scoped_agents_md_path("app"), "app/.ai/AGENTS.md")
        self.assertEqual(self.repo.scoped_agents_md_path("server"), "server/.ai/AGENTS.md")
        self.assertEqual(self.repo.scoped_agents_md_path("firmware"), "firmware/.ai/AGENTS.md")
        self.assertEqual(self.repo.scoped_agents_md_path("repo-wide"), ".ai/AGENTS.md")
        self.assertEqual(self.repo.scoped_agents_md_path("docs"), ".ai/AGENTS.md")


class TestRepoRealityGit(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls._tmp = tempfile.TemporaryDirectory()
        cls.root = Path(cls._tmp.name)
        _git(cls.root, "init", "-q")
        _git(cls.root, "config", "user.email", "test@example.com")
        _git(cls.root, "config", "user.name", "Test")
        (cls.root / "referenced.txt").write_text("uses fixtures/cases.json here\n", encoding="utf-8")
        _git(cls.root, "add", ".")
        _git(cls.root, "commit", "-q", "-m", "initial")
        sha = subprocess.run(
            ["git", "-C", str(cls.root), "rev-parse", "HEAD"],
            check=True,
            capture_output=True,
            text=True,
        ).stdout.strip()
        cls.head_sha = sha

    @classmethod
    def tearDownClass(cls):
        cls._tmp.cleanup()

    def test_references_in_repo_counts_tracked_hits(self):
        repo = RepoReality(self.root)

        self.assertEqual(repo.references_in_repo("fixtures/cases.json"), 1)
        self.assertEqual(repo.references_in_repo("never-mentioned-literal"), 0)
        self.assertEqual(repo.references_in_repo(""), 0)

    def test_commit_exists(self):
        repo = RepoReality(self.root)

        self.assertTrue(repo.commit_exists(self.head_sha))
        self.assertTrue(repo.commit_exists(self.head_sha[:12]))
        self.assertFalse(repo.commit_exists("0" * 40))
        self.assertFalse(repo.commit_exists("not-a-sha"))
        self.assertFalse(repo.commit_exists(""))


if __name__ == "__main__":
    unittest.main()
