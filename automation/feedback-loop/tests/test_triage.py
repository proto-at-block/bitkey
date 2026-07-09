"""Tests for BKW-53 human triage report generation."""

from __future__ import annotations

import os
import sys
import unittest
from typing import Literal

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from feedback_loop.models import (  # noqa: E402
    Cluster,
    Destination,
    Exclusion,
    NormalizedSignal,
    PrimaryClass,
    RawSignal,
)
from feedback_loop.pipeline.triage import build_triage_report  # noqa: E402

Severity = Literal["critical", "high", "medium", "low"]


def signal(
    source_id: str,
    *,
    pr_number: int = 123,
    body: str = "Missing validation.",
    primary_class: PrimaryClass = "miss",
    severity: Severity = "medium",
    confidence: float = 0.8,
    manual_triage: bool = False,
    excluded: bool = False,
    destination: Destination | None = "test_or_linter",
) -> NormalizedSignal:
    raw = RawSignal(
        kind="review_comment",
        source_id=source_id,
        source_url=f"https://github.com/squareup/wallet/pull/{pr_number}#{source_id}",
        repo="squareup/wallet",
        pr_number=pr_number,
        captured_at="2026-05-04T00:00:00Z",
        body=body,
    )
    return NormalizedSignal(
        raw=raw,
        kind=raw.kind,
        source="review_comment",
        source_id=source_id,
        source_url=raw.source_url,
        repo=raw.repo,
        pr_number=pr_number,
        captured_at=raw.captured_at,
        harvest_version="test",
        body=body,
        primary_class=primary_class,
        severity=severity,
        confidence=confidence,
        rationale="classifier rationale",
        suggested_destination=None if manual_triage or excluded else destination,
        evidence_ids=[source_id],
        manual_triage=manual_triage,
        exclusion=(
            Exclusion(reason="style_nit", summary="style nit")
            if excluded
            else None
        ),
    )


class TestTriageReport(unittest.TestCase):
    def test_builds_markdown_and_machine_summary(self):
        cluster = Cluster(
            theme="miss:app:validation:test_or_linter",
            signals=[
                signal("review_comment:1"),
                signal("review_comment:2", pr_number=456),
                signal("review_comment:3", pr_number=789),
            ],
            area="app",
            severity="medium",
            frequency=3,
            rank=6.0,
            suggested_destination="test_or_linter",
            summary="3 PR(s), 3 signal(s), medium severity: miss in app about validation",
            representative_examples=["review_comment:1: Missing validation."],
            source_urls=[
                "https://github.com/squareup/wallet/pull/123#review_comment:1",
                "https://github.com/squareup/wallet/pull/456#review_comment:2",
                "https://github.com/squareup/wallet/pull/789#review_comment:3",
            ],
        )

        report = build_triage_report([cluster])

        self.assertIn("# Feedback Loop Triage Report", report.markdown)
        self.assertIn("Decision: `convert_to_mechanical_check`", report.markdown)
        self.assertIn("https://github.com/squareup/wallet/pull/123#review_comment:1", report.markdown)
        self.assertEqual(report.summary[0]["theme"], "miss:app:validation:test_or_linter")
        self.assertEqual(report.summary[0]["decision"], "convert_to_mechanical_check")
        self.assertEqual(report.summary[0]["confidence"], 0.8)
        self.assertEqual(
            report.summary[0]["source_ids"],
            ["review_comment:1", "review_comment:2", "review_comment:3"],
        )

    def test_thin_noncritical_clusters_gather_more_evidence(self):
        cluster = Cluster(
            theme="miss:app:validation:test_or_linter",
            signals=[signal("review_comment:1")],
            severity="medium",
            frequency=1,
            rank=2.0,
            suggested_destination="test_or_linter",
            representative_examples=["review_comment:1: Missing validation."],
        )

        report = build_triage_report([cluster])

        self.assertEqual(report.summary[0]["decision"], "gather_more_evidence")
        self.assertIn("medium clusters need at least 3 distinct PR(s)", report.markdown)

    def test_low_nonmechanical_clusters_gather_more_evidence(self):
        cluster = Cluster(
            theme="miss:docs:guidance:agents_check",
            signals=[signal("review_comment:1")],
            severity="low",
            frequency=5,
            rank=5.0,
            suggested_destination="agents_check",
            representative_examples=["review_comment:1: Ambiguous guidance."],
        )

        report = build_triage_report([cluster])

        self.assertEqual(report.summary[0]["decision"], "gather_more_evidence")
        self.assertIn("Low-severity clusters must be mechanically enforceable", report.markdown)

    def test_routes_manual_triage_clusters_to_more_evidence(self):
        cluster = Cluster(
            theme="miss:app:validation:manual_triage",
            signals=[signal("review_comment:1", confidence=0.45, manual_triage=True)],
            severity="medium",
            frequency=1,
            rank=2.0,
            representative_examples=["review_comment:1: Missing validation."],
        )

        report = build_triage_report([cluster])

        self.assertEqual(report.summary[0]["decision"], "gather_more_evidence")
        self.assertTrue(report.summary[0]["manual_triage"])
        self.assertIn("Resolve low-confidence/manual-triage signals", report.markdown)
        self.assertIn("medium clusters need at least 3 distinct PR(s)", report.markdown)

    def test_routes_excluded_only_clusters_to_ignore(self):
        cluster = Cluster(
            theme="nit:repo-wide:typo:excluded",
            signals=[signal("review_comment:1", excluded=True)],
            severity="low",
            frequency=1,
            rank=0.0,
            representative_examples=["review_comment:1: Nit typo."],
        )

        report = build_triage_report([cluster])

        self.assertEqual(report.summary[0]["decision"], "ignore")
        self.assertTrue(report.summary[0]["excluded_only"])
        self.assertIn("Confirm excluded-only feedback", report.markdown)

    def test_review_only_clusters_keep_report_populated(self):
        cluster = Cluster(
            theme="not_actionable:repo-wide:general-concern:review_only",
            signals=[
                signal(
                    "review:1",
                    body="General concern.",
                    primary_class="not_actionable",
                    severity="low",
                    destination=None,
                )
            ],
            severity="low",
            frequency=1,
            rank=0.0,
            representative_examples=["review:1: General concern."],
        )

        report = build_triage_report([cluster])

        self.assertNotIn("No classified clusters found.", report.markdown)
        self.assertIn("not_actionable:repo-wide:general-concern:review_only", report.markdown)
        self.assertIn("General concern.", report.markdown)

    def test_empty_report_is_understandable(self):
        report = build_triage_report([])

        self.assertEqual(report.summary, [])
        self.assertIn("No classified clusters found.", report.markdown)


if __name__ == "__main__":
    unittest.main()
