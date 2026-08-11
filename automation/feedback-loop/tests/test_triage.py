"""Tests for the triage report over LLM-clustered themes."""

from __future__ import annotations

import os
import sys
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from feedback_loop.models import (  # noqa: E402
    Cluster,
    Exclusion,
    NormalizedSignal,
    RawSignal,
    Resolution,
)
from feedback_loop.pipeline.triage import build_triage_report, facts_only_report  # noqa: E402


def signal(
    source_id: str,
    *,
    pr_number: int = 1,
    primary_class: str = "miss",
    severity: str = "medium",
    confidence: float = 0.8,
    manual_triage: bool = False,
    excluded: bool = False,
    resolution: Resolution | None = None,
) -> NormalizedSignal:
    raw = RawSignal(
        kind="review_comment",
        source_id=source_id,
        source_url=f"https://github.com/squareup/wallet/pull/{pr_number}#discussion_{source_id}",
        repo="squareup/wallet",
        pr_number=pr_number,
        captured_at="2026-06-09T00:00:00Z",
    )
    return NormalizedSignal(
        raw=raw,
        kind=raw.kind,
        source="review_comment",
        source_id=raw.source_id,
        source_url=raw.source_url,
        repo=raw.repo,
        pr_number=raw.pr_number,
        captured_at=raw.captured_at,
        harvest_version="test",
        body="Please add validation coverage.",
        primary_class=primary_class,
        severity=severity,
        confidence=confidence,
        manual_triage=manual_triage,
        exclusion=Exclusion(reason="style_nit", summary="nit") if excluded else None,
        resolution=resolution,
    )


def cluster(
    slug: str,
    *,
    decision: str,
    severity: str = "medium",
    frequency: int = 3,
    rank: float = 6.0,
    destination: str = "agents_check",
    signals: list[NormalizedSignal] | None = None,
    matched_issue_url: str = "",
) -> Cluster:
    members = signals if signals is not None else [signal(f"{slug}:1"), signal(f"{slug}:2")]
    return Cluster(
        slug=slug,
        signals=members,
        title=f"Theme {slug}",
        area="automation",
        severity=severity,
        frequency=frequency,
        current_pr_numbers=tuple(sorted({item.pr_number for item in members})),
        merged_pr_numbers=tuple(range(1, frequency + 1)),
        rank=rank,
        suggested_destination=destination,
        decision=decision,
        matched_issue_url=matched_issue_url,
        summary=f"Recurring {slug} theme.",
        representative_examples=[f"{slug}:1: Please add validation coverage."],
        source_urls=[item.source_url for item in members],
    )


class TestTriageReport(unittest.TestCase):
    def test_renders_decisions_and_memory_matches(self):
        report = build_triage_report(
            [
                cluster("validation-coverage", decision="promote"),
                cluster(
                    "known-theme",
                    decision="gather_more_evidence",
                    frequency=1,
                    rank=2.0,
                    matched_issue_url="https://linear.app/squareup/issue/BKW-77/x",
                ),
            ]
        )

        self.assertIn("## 1. Theme validation-coverage", report.markdown)
        self.assertIn("- Decision: `promote`", report.markdown)
        self.assertIn("- Matched memory: https://linear.app/squareup/issue/BKW-77/x", report.markdown)
        self.assertIn("- Matched memory: `new theme`", report.markdown)
        self.assertEqual(report.summary[0]["slug"], "validation-coverage")
        self.assertEqual(report.summary[0]["decision"], "promote")
        self.assertEqual(report.summary[1]["decision"], "gather_more_evidence")

    def test_concise_report_hides_audit_only_decisions(self):
        clusters = [
            cluster("promote-me", decision="promote"),
            cluster("covered", decision="already_covered"),
            cluster("ignored", decision="ignore"),
        ]

        full = build_triage_report(clusters)
        concise = build_triage_report(clusters, include_audit_only=False)

        self.assertEqual(len(full.summary), 3)
        self.assertEqual(len(concise.summary), 1)
        self.assertEqual(concise.summary[0]["slug"], "promote-me")
        self.assertNotIn("Theme covered", concise.markdown)

    def test_volume_summary_counts_audit_signals_and_resolutions(self):
        resolved = signal(
            "resolved:1",
            resolution=Resolution(
                state="resolved_with_durable_coverage",
                evidence_signal_ids=("commit:abc",),
                coverage_paths=("app/src/test/FooTest.kt",),
            ),
        )
        clusters = [cluster("theme-a", decision="promote", signals=[resolved, signal("open:1")])]
        audit = [
            signal("nit:1", primary_class="nit", excluded=True),
            signal("fp:1", primary_class="false_positive"),
        ]

        report = build_triage_report(clusters, audit_signals=audit)

        volume = report.comment_volume_summary
        self.assertEqual(volume["total"], 4)
        self.assertEqual(volume["visible"], 2)
        self.assertEqual(volume["audit_only"], 2)
        self.assertEqual(volume["acknowledgement_or_noise"], 2)
        self.assertEqual(volume["already_covered"], 1)
        self.assertEqual(volume["unresolved"], 3)
        self.assertIn("## Audit-only signals (not clustered)", report.markdown)
        self.assertIn("- excluded:style_nit: `1`", report.markdown)
        self.assertIn("- false_positive: `1`", report.markdown)

    def test_open_questions_explain_gather_more_evidence(self):
        report = build_triage_report(
            [
                cluster(
                    "thin-theme",
                    decision="gather_more_evidence",
                    severity="medium",
                    frequency=1,
                )
            ]
        )

        questions = report.summary[0]["open_questions"]
        self.assertTrue(
            any("at least 3 distinct PR(s)" in question for question in questions),
            questions,
        )

    def test_open_questions_flag_manual_triage_and_low_severity(self):
        members = [signal("low:1", severity="low", manual_triage=True)]
        report = build_triage_report(
            [
                cluster(
                    "low-theme",
                    decision="gather_more_evidence",
                    severity="low",
                    frequency=6,
                    destination="docs",
                    signals=members,
                )
            ]
        )

        questions = report.summary[0]["open_questions"]
        self.assertTrue(any("manual-triage" in question for question in questions))
        self.assertTrue(any("mechanically enforceable" in question for question in questions))

    def test_empty_report_messages(self):
        full = build_triage_report([])
        concise = build_triage_report([], include_audit_only=False)

        self.assertIn("No classified clusters found.", full.markdown)
        self.assertIn("No actionable or manual-triage clusters found.", concise.markdown)


class TestFactsOnlyReport(unittest.TestCase):
    def test_inventories_signals_without_classification(self):
        signals = [signal("a:1"), signal("a:2")]
        noise = [signal("ack:1", primary_class="not_actionable", excluded=True)]

        report = facts_only_report(signals, noise)

        self.assertIn("Facts-Only Report", report.markdown)
        self.assertIn("- review_comment: `2`", report.markdown)
        self.assertIn("noise (excluded pre-classification): `1`", report.markdown)
        self.assertEqual(report.summary, [])
        self.assertEqual(report.comment_volume_summary["total"], 3)
        self.assertEqual(report.comment_volume_summary["acknowledgement_or_noise"], 1)


if __name__ == "__main__":
    unittest.main()
