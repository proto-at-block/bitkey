"""Tests for BKW-83 exclusion rules."""

from __future__ import annotations

import os
import sys
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from feedback_loop.config import RunConfig  # noqa: E402
from feedback_loop.models import Cluster, Exclusion, NormalizedSignal, Proposal, RawSignal  # noqa: E402
from feedback_loop.pipeline.classify import classify  # noqa: E402
from feedback_loop.pipeline.normalize import normalize  # noqa: E402


def raw_signal(
    kind: str,
    source_id: str,
    *,
    body: str,
    created_at: str = "2026-05-04T01:00:00Z",
    raw: dict | None = None,
) -> RawSignal:
    return RawSignal(
        kind=kind,
        source_id=source_id,
        source_url=f"https://github.com/squareup/wallet/pull/123#{source_id}",
        repo="squareup/wallet",
        pr_number=123,
        captured_at="2026-05-04T00:00:00Z",
        created_at=created_at,
        body=body,
        raw=raw or {},
    )


def classify_signals(*signals: RawSignal) -> list[NormalizedSignal]:
    cfg = RunConfig(harvest_version="test")
    return classify(cfg, normalize(cfg, list(signals)))


def require_exclusion(signal: NormalizedSignal) -> Exclusion:
    exclusion = signal.exclusion
    if exclusion is None:
        raise AssertionError("expected signal to be excluded")
    return exclusion


class TestExclusions(unittest.TestCase):
    def test_excludes_style_nits_with_auditable_reason(self):
        item = classify_signals(
            raw_signal("review_comment", "review_comment:1", body="Nit: typo in this variable name.")
        )[0]

        exclusion = require_exclusion(item)
        self.assertTrue(item.is_excluded)
        self.assertEqual(item.primary_class, "nit")
        self.assertEqual(exclusion.reason, "style_nit")
        self.assertFalse(exclusion.summarize_as_context)
        self.assertIn("excluded", item.secondary_tags)
        self.assertIn("excluded:nit", item.secondary_tags)
        self.assertIn("excluded: style, wording, formatting, or spelling nit", item.rationale)
        self.assertIsNone(item.suggested_destination)
        self.assertFalse(item.manual_triage)

    def test_excludes_product_decisions_but_keeps_context_flag(self):
        item = classify_signals(
            raw_signal(
                "issue_comment",
                "issue_comment:1",
                body="Do we want this UX copy for launch, or is that a PM call?",
            )
        )[0]

        exclusion = require_exclusion(item)
        self.assertEqual(item.primary_class, "product_decision")
        self.assertEqual(exclusion.reason, "product_decision")
        self.assertTrue(exclusion.summarize_as_context)
        self.assertIn("excluded:product_decision", item.secondary_tags)

    def test_likely_miss_question_is_not_excluded_as_speculative(self):
        feedback = raw_signal(
            "issue_comment",
            "issue_comment:1",
            body="Can we validate this edge case?",
            created_at="2026-05-04T01:00:00Z",
            raw={"thread_id": "discussion:1"},
        )
        reply = raw_signal(
            "issue_comment",
            "issue_comment:2",
            body="Fixed, added coverage.",
            created_at="2026-05-04T01:30:00Z",
            raw={"thread_id": "discussion:1"},
        )
        commit = raw_signal(
            "commit",
            "commit:def",
            body="address validation coverage",
            created_at="2026-05-04T02:00:00Z",
        )

        item = classify_signals(feedback, reply, commit)[0]

        self.assertIsNone(item.exclusion)
        self.assertIn("likely_miss_correlation", item.secondary_tags)

    def test_manual_triage_miss_is_not_overridden_by_product_or_question_exclusions(self):
        item = classify_signals(
            raw_signal(
                "review_comment",
                "review_comment:1",
                body="Missing validation for this product requirement?",
            )
        )[0]

        self.assertEqual(item.primary_class, "miss")
        self.assertTrue(item.manual_triage)
        self.assertIsNone(item.exclusion)
        self.assertIsNone(item.suggested_destination)
        self.assertIn("manual_triage", item.secondary_tags)

    def test_excluded_only_clusters_cannot_create_proposals(self):
        excluded = classify_signals(
            raw_signal("review_comment", "review_comment:1", body="Nit: punctuation.")
        )[0]
        cluster = Cluster(theme="style nits", signals=[excluded])

        self.assertTrue(cluster.excluded_only)
        self.assertEqual(cluster.promotable_signals, [])
        with self.assertRaises(ValueError):
            Proposal(cluster=cluster, destination="docs", summary="Add style guidance.")

    def test_mixed_clusters_keep_promotable_signals(self):
        excluded = classify_signals(
            raw_signal("review_comment", "review_comment:1", body="Nit: punctuation.")
        )[0]
        promotable = classify_signals(
            raw_signal("review_comment", "review_comment:2", body="Missing validation.")
        )[0]
        cluster = Cluster(theme="mixed", signals=[excluded, promotable])

        self.assertFalse(cluster.excluded_only)
        self.assertEqual(cluster.promotable_signals, [promotable])


if __name__ == "__main__":
    unittest.main()
