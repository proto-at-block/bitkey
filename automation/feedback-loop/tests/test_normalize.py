"""Tests for normalize-only feedback-loop records."""

from __future__ import annotations

import os
import sys
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from feedback_loop.config import RunConfig  # noqa: E402
from feedback_loop.models import RawSignal  # noqa: E402
from feedback_loop.pipeline.normalize import normalize  # noqa: E402


def raw_signal(**updates) -> RawSignal:
    signal = RawSignal(
        kind="bot_review",
        source_id="bot_review:squareup/wallet#123:444",
        source_url="https://github.com/squareup/wallet/pull/123#issuecomment-444",
        repo="squareup/wallet",
        pr_number=123,
        captured_at="2026-05-04T01:00:00Z",
        author="github-actions[bot]",
        author_association="MEMBER",
        created_at="2026-05-04T00:59:00Z",
        body="Automated review finding",
        is_bot=True,
        raw={
            "provider": "codex_security_review",
            "body": "Automated review finding",
            "nested": {"url": "https://github.com/squareup/wallet/actions/runs/1"},
        },
    )
    for key, value in updates.items():
        setattr(signal, key, value)
    return signal


class TestNormalize(unittest.TestCase):
    def test_normalizes_provenance_and_body_in_memory(self):
        signal = raw_signal()

        normalized = normalize(RunConfig(harvest_version="7"), [signal])

        self.assertEqual(len(normalized), 1)
        item = normalized[0]
        self.assertIs(item.raw, signal)
        self.assertEqual(item.kind, "bot_review")
        self.assertEqual(item.source, "codex_security_review")
        self.assertEqual(item.source_id, signal.source_id)
        self.assertEqual(item.source_url, signal.source_url)
        self.assertEqual(item.repo, "squareup/wallet")
        self.assertEqual(item.pr_number, 123)
        self.assertEqual(item.captured_at, "2026-05-04T01:00:00Z")
        self.assertEqual(item.harvest_version, "7")
        self.assertEqual(item.author, "github-actions[bot]")
        self.assertTrue(item.is_bot)
        self.assertEqual(item.body, "Automated review finding")
        self.assertEqual(item.raw_metadata["provider"], "codex_security_review")
        self.assertEqual(
            item.raw_metadata["nested"]["url"],
            "https://github.com/squareup/wallet/actions/runs/1",
        )

    def test_copies_raw_metadata_before_returning(self):
        signal = raw_signal()

        normalized = normalize(RunConfig(), [signal])
        signal.raw["nested"]["url"] = "changed"

        self.assertEqual(
            normalized[0].raw_metadata["nested"]["url"],
            "https://github.com/squareup/wallet/actions/runs/1",
        )

    def test_falls_back_to_kind_when_source_metadata_is_missing(self):
        normalized = normalize(RunConfig(), [raw_signal(raw={})])

        self.assertEqual(normalized[0].source, "bot_review")


if __name__ == "__main__":
    unittest.main()
