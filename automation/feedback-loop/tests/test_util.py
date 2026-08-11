"""Tests for shared feedback-loop utilities."""

from __future__ import annotations

import os
import sys
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from feedback_loop.util import highest_severity  # noqa: E402


class TestHighestSeverity(unittest.TestCase):
    def test_unknown_severity_normalizes_to_low(self):
        self.assertEqual(highest_severity(["unknown"]), "low")
        self.assertEqual(highest_severity([""]), "low")

    def test_mixed_severities_choose_highest_known(self):
        self.assertEqual(highest_severity(["unknown", "medium", "low"]), "medium")
        self.assertEqual(highest_severity(["low", "critical", "n/a"]), "critical")


if __name__ == "__main__":
    unittest.main()
