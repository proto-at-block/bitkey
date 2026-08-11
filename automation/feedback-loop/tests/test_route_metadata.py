"""Tests for route metadata helpers."""

from __future__ import annotations

import os
import sys
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from feedback_loop.models import ProposalFileChange  # noqa: E402
from feedback_loop.route_metadata import change_set_id  # noqa: E402


class TestChangeSetId(unittest.TestCase):
    def test_change_set_id_includes_file_content_and_mode(self):
        first = change_set_id(
            "llm:learn:agents_check",
            [
                ProposalFileChange(
                    path=".agents/checks/example.md",
                    mode="create_or_update",
                    content="one\n",
                )
            ],
        )
        changed_content = change_set_id(
            "llm:learn:agents_check",
            [
                ProposalFileChange(
                    path=".agents/checks/example.md",
                    mode="create_or_update",
                    content="two\n",
                )
            ],
        )
        changed_mode = change_set_id(
            "llm:learn:agents_check",
            [
                ProposalFileChange(
                    path=".agents/checks/example.md",
                    mode="unified_diff",
                    content="one\n",
                )
            ],
        )

        self.assertNotEqual(first, changed_content)
        self.assertNotEqual(first, changed_mode)


if __name__ == "__main__":
    unittest.main()
