"""Tests for BKW-76 deterministic feedback clustering."""

from __future__ import annotations

import os
import sys
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from feedback_loop.config import RunConfig  # noqa: E402
from feedback_loop.models import NormalizedSignal, RawSignal  # noqa: E402
from feedback_loop.pipeline.classify import classify  # noqa: E402
from feedback_loop.pipeline.cluster import cluster, merge_clusters  # noqa: E402
from feedback_loop.pipeline.normalize import normalize  # noqa: E402


def raw_signal(
    kind: str,
    source_id: str,
    *,
    pr_number: int,
    body: str = "",
    created_at: str = "2026-05-04T01:00:00Z",
    path: str | None = None,
    line: int | None = None,
    raw: dict | None = None,
) -> RawSignal:
    return RawSignal(
        kind=kind,
        source_id=source_id,
        source_url=f"https://github.com/squareup/wallet/pull/{pr_number}#{source_id}",
        repo="squareup/wallet",
        pr_number=pr_number,
        captured_at="2026-05-04T00:00:00Z",
        created_at=created_at,
        body=body,
        path=path,
        line=line,
        raw=raw or {},
    )


def classified(*signals: RawSignal) -> list[NormalizedSignal]:
    cfg = RunConfig(harvest_version="test")
    return classify(cfg, normalize(cfg, list(signals)))


class TestCluster(unittest.TestCase):
    def test_clusters_repeated_misses_by_theme_and_frequency(self):
        signals = classified(
            raw_signal(
                "review_comment",
                "review_comment:1",
                pr_number=101,
                body="Please add a regression test here.",
                path="automation/feedback-loop/feedback_loop/pipeline/harvest.py",
                line=12,
            ),
            raw_signal(
                "diff_hunk",
                "hunk:101",
                pr_number=101,
                path="automation/feedback-loop/feedback_loop/pipeline/harvest.py",
                raw={"new_start": 10, "new_count": 5},
            ),
            raw_signal(
                "commit",
                "commit:101",
                pr_number=101,
                body="fix review comment with regression test",
                created_at="2026-05-04T02:00:00Z",
            ),
            raw_signal(
                "review_comment",
                "review_comment:2",
                pr_number=202,
                body="Please add a regression test for this branch.",
                path="automation/feedback-loop/feedback_loop/pipeline/normalize.py",
                line=20,
            ),
            raw_signal(
                "diff_hunk",
                "hunk:202",
                pr_number=202,
                path="automation/feedback-loop/feedback_loop/pipeline/normalize.py",
                raw={"new_start": 18, "new_count": 5},
            ),
            raw_signal(
                "commit",
                "commit:202",
                pr_number=202,
                body="fix review comment with regression test",
                created_at="2026-05-04T02:00:00Z",
            ),
        )

        clusters = cluster(RunConfig(), signals)

        self.assertEqual(len(clusters), 1)
        item = clusters[0]
        self.assertEqual(item.frequency, 2)
        self.assertEqual(item.severity, "high")
        self.assertEqual(item.rank, 8.0)
        self.assertEqual(item.area, "automation")
        self.assertEqual(item.suggested_destination, "test_or_linter")
        self.assertIn("miss:automation:test:test_or_linter", item.theme)
        self.assertEqual(len(item.representative_examples), 2)
        self.assertEqual(len(item.source_urls), 2)

    def test_ranks_by_severity_times_distinct_pr_frequency(self):
        signals = classified(
            raw_signal(
                "review_comment",
                "critical:1",
                pr_number=101,
                body="Missing security validation.",
                path="server/auth.rs",
            ),
            raw_signal(
                "review_comment",
                "medium:1",
                pr_number=202,
                body="Missing validation.",
                path="app/Feature.kt",
            ),
            raw_signal(
                "review_comment",
                "medium:2",
                pr_number=303,
                body="Missing validation.",
                path="app/Other.kt",
            ),
        )

        clusters = cluster(RunConfig(), signals)

        self.assertGreater(clusters[0].rank, clusters[1].rank)
        self.assertEqual(clusters[0].severity, "critical")

    def test_excluded_only_clusters_are_retained_with_zero_rank(self):
        signals = classified(
            raw_signal("review_comment", "nit:1", pr_number=101, body="Nit: typo.")
        )

        clusters = cluster(RunConfig(), signals)

        self.assertEqual(len(clusters), 1)
        self.assertTrue(clusters[0].excluded_only)
        self.assertEqual(clusters[0].rank, 0.0)
        self.assertIsNone(clusters[0].suggested_destination)

    def test_not_actionable_feedback_is_retained_as_review_only_cluster(self):
        signals = classified(
            raw_signal("review", "review:1", pr_number=101, body="General concern.")
        )

        clusters = cluster(RunConfig(), signals)

        self.assertEqual(len(clusters), 1)
        self.assertEqual(clusters[0].signals[0].primary_class, "not_actionable")
        self.assertFalse(clusters[0].excluded_only)
        self.assertEqual(clusters[0].rank, 0.0)
        self.assertIsNone(clusters[0].suggested_destination)
        self.assertIn("not_actionable:repo-wide:general-concern:review_only", clusters[0].theme)

    def test_false_positive_clusters_are_retained_for_triage(self):
        signals = classified(
            raw_signal(
                "bot_review",
                "bot_review:1",
                pr_number=101,
                body="False positive, this is safe as-is.",
                raw={"provider": "codex_security_review"},
            )
        )

        clusters = cluster(RunConfig(), signals)

        self.assertEqual(len(clusters), 1)
        self.assertIn("false_positive", clusters[0].theme)
        self.assertFalse(clusters[0].excluded_only)
        self.assertIsNone(clusters[0].suggested_destination)

    def test_merge_clusters_combines_per_pr_clusters_by_theme(self):
        first_pr_clusters = cluster(
            RunConfig(),
            classified(
                raw_signal(
                    "review_comment",
                    "review_comment:1",
                    pr_number=101,
                    body="Please add a regression test here.",
                    path="automation/feedback-loop/feedback_loop/pipeline/harvest.py",
                    line=12,
                ),
                raw_signal(
                    "diff_hunk",
                    "hunk:101",
                    pr_number=101,
                    path="automation/feedback-loop/feedback_loop/pipeline/harvest.py",
                    raw={"new_start": 10, "new_count": 5},
                ),
                raw_signal(
                    "commit",
                    "commit:101",
                    pr_number=101,
                    body="fix review comment with regression test",
                    created_at="2026-05-04T02:00:00Z",
                ),
            ),
        )
        second_pr_clusters = cluster(
            RunConfig(),
            classified(
                raw_signal(
                    "review_comment",
                    "review_comment:2",
                    pr_number=202,
                    body="Please add a regression test for this branch.",
                    path="automation/feedback-loop/feedback_loop/pipeline/normalize.py",
                    line=20,
                ),
                raw_signal(
                    "diff_hunk",
                    "hunk:202",
                    pr_number=202,
                    path="automation/feedback-loop/feedback_loop/pipeline/normalize.py",
                    raw={"new_start": 18, "new_count": 5},
                ),
                raw_signal(
                    "commit",
                    "commit:202",
                    pr_number=202,
                    body="fix review comment with regression test",
                    created_at="2026-05-04T02:00:00Z",
                ),
            ),
        )

        merged = merge_clusters(RunConfig(), [*first_pr_clusters, *second_pr_clusters])

        self.assertEqual(len(merged), 1)
        self.assertEqual(merged[0].frequency, 2)
        self.assertEqual(merged[0].rank, 8.0)
        self.assertEqual(len(merged[0].signals), 2)

    def test_cluster_order_is_stable_across_reruns(self):
        signals = classified(
            raw_signal(
                "review_comment",
                "review_comment:2",
                pr_number=202,
                body="Missing validation.",
                path="app/Other.kt",
            ),
            raw_signal(
                "review_comment",
                "review_comment:1",
                pr_number=101,
                body="Missing validation.",
                path="app/Feature.kt",
            ),
        )

        first = cluster(RunConfig(), signals)
        second = cluster(RunConfig(), list(reversed(signals)))

        self.assertEqual([item.theme for item in first], [item.theme for item in second])
        self.assertEqual(
            [item.representative_examples for item in first],
            [item.representative_examples for item in second],
        )


if __name__ == "__main__":
    unittest.main()
