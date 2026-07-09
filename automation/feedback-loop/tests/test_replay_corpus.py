"""Tests for replay corpus loading and validation."""

from __future__ import annotations

import json
import os
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from feedback_loop.replay import load_replay_corpus  # noqa: E402


class TestReplayCorpus(unittest.TestCase):
    def test_loads_committed_corpus(self):
        cases = load_replay_corpus()

        self.assertGreaterEqual(len(cases), 3)
        self.assertEqual(len({case.case_id for case in cases}), len(cases))
        for case in cases:
            self.assertEqual(case.repo, "squareup/wallet")
            self.assertGreater(case.pr_number, 0)
            self.assertTrue(case.pr_url.startswith("https://github.com/squareup/wallet/pull/"))
            self.assertIn("#", case.source_comment_url)
            self.assertTrue(case.commit_range.base)
            self.assertTrue(case.commit_range.head)
            self.assertTrue(case.changed_files)
            self.assertTrue(case.summary)
            self.assertTrue(case.expected_finding)

    def test_rejects_missing_required_fields(self):
        path = self.write_corpus({"version": 1, "cases": [{"id": "missing-fields"}]})

        with self.assertRaisesRegex(ValueError, "missing fields"):
            load_replay_corpus(path)

    def test_rejects_duplicate_case_ids(self):
        first = self.valid_case()
        second = self.valid_case()
        second["pr_number"] = 456
        path = self.write_corpus({"version": 1, "cases": [first, second]})

        with self.assertRaisesRegex(ValueError, "duplicate case id\\(s\\): valid"):
            load_replay_corpus(path)

    def test_rejects_invalid_taxonomy_values(self):
        case = self.valid_case()
        case["miss_class"] = "style"
        path = self.write_corpus({"version": 1, "cases": [case]})

        with self.assertRaisesRegex(ValueError, "invalid miss_class"):
            load_replay_corpus(path)

    def test_rejects_invalid_expected_severity(self):
        case = self.valid_case()
        case["expected_severity"] = "urgent"
        path = self.write_corpus({"version": 1, "cases": [case]})

        with self.assertRaisesRegex(ValueError, "invalid expected_severity"):
            load_replay_corpus(path)

    def test_rejects_cases_without_changed_files(self):
        case = self.valid_case()
        case["changed_files"] = []
        path = self.write_corpus({"version": 1, "cases": [case]})

        with self.assertRaisesRegex(ValueError, "changed_files must not be empty"):
            load_replay_corpus(path)

    def write_corpus(self, data: dict) -> Path:
        handle = tempfile.NamedTemporaryFile("w", encoding="utf-8", delete=False)
        self.addCleanup(lambda: os.path.exists(handle.name) and os.unlink(handle.name))
        with handle:
            json.dump(data, handle)
        return Path(handle.name)

    def valid_case(self) -> dict:
        return {
            "id": "valid",
            "repo": "squareup/wallet",
            "pr_number": 123,
            "pr_url": "https://github.com/squareup/wallet/pull/123",
            "commit_range": {
                "base": "base",
                "head": "head",
                "merge_commit": "merge",
            },
            "changed_files": ["app/example.kt"],
            "miss_class": "miss",
            "source_comment_url": "https://github.com/squareup/wallet/pull/123#discussion_r1",
            "expected_finding": "Flag the missed behavior.",
            "summary": "Short case summary.",
        }


if __name__ == "__main__":
    unittest.main()
