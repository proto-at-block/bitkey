"""Tests for replay scoring rubric."""

from __future__ import annotations

import os
import sys
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from feedback_loop.models import (  # noqa: E402
    Cluster,
    NormalizedSignal,
    Proposal,
    RawSignal,
    ReplayCase,
    ReplayCommitRange,
    ReplayFinding,
    ReplayReport,
    Severity,
)
from feedback_loop.replay import run_replay_harness  # noqa: E402
from feedback_loop.rubric import (  # noqa: E402
    RubricOverride,
    rubric_markdown,
    score_proposal,
)


class TestRubric(unittest.TestCase):
    def test_passes_grounded_actionable_proposal_that_catches_replay_misses(self):
        cases = [replay_case("case-1"), replay_case("case-2")]
        report = replay_report(cases)

        result = score_proposal(actionable_proposal(cases), report)

        self.assertTrue(result.passed)
        self.assertTrue(result.passed_without_override)
        self.assertEqual(result.scores.recall, 1.0)
        self.assertEqual(result.scores.noise_cost, 0.0)
        self.assertEqual(result.scores.actionability, 1.0)
        self.assertEqual(result.scores.source_grounding, 1.0)

        markdown = rubric_markdown(result)
        self.assertIn("Status: PASS", markdown)
        self.assertIn("| Recall | 1.00 |", markdown)

    def test_rejects_vague_advice_even_when_replay_catches_misses(self):
        cases = [replay_case("case-1"), replay_case("case-2")]
        report = replay_report(cases)

        result = score_proposal(vague_proposal(cases), report)

        self.assertFalse(result.passed)
        self.assertIn("proposal_not_actionable", result.blocking_reasons)
        self.assertIn("proposal_not_source_grounded", result.blocking_reasons)

    def test_records_noise_cost_and_rejects_noisy_proposals(self):
        cases = [replay_case("case-1"), replay_case("case-2")]

        def noisy_runner(item):
            return [
                grounded_finding(item),
                ReplayFinding(case_id=f"extra-{item.case_id}", summary="Noisy finding."),
            ]

        report = replay_report(cases, proposed_runner=noisy_runner)

        result = score_proposal(actionable_proposal(cases), report)

        self.assertFalse(result.passed)
        self.assertEqual(result.scores.noise_cost, 1.0)
        self.assertIn("noise_cost_above_threshold", result.blocking_reasons)
        self.assertIn("extra findings recorded", rubric_markdown(result))

    def test_runtime_failures_block_publication(self):
        cases = [replay_case("case-1"), replay_case("case-2")]

        def broken_runner(item):
            raise RuntimeError("runner failed")

        report = replay_report(cases, proposed_runner=broken_runner)

        result = score_proposal(actionable_proposal(cases), report)

        self.assertFalse(result.passed)
        self.assertEqual(result.scores.runtime_failures, 2)
        self.assertIn("runtime_failures_present", result.blocking_reasons)
        self.assertIn("runtime failures block", rubric_markdown(result))

    def test_rejects_incorrect_severity_when_replay_cases_declare_it(self):
        cases = [
            replay_case("case-1", expected_severity="high"),
            replay_case("case-2", expected_severity="high"),
        ]
        report = replay_report(cases)

        result = score_proposal(actionable_proposal(cases, severity="medium"), report)

        self.assertFalse(result.passed)
        self.assertEqual(result.scores.severity, 0.0)
        self.assertIn("severity_mismatch", result.blocking_reasons)

    def test_manual_override_allows_high_severity_sparse_replay_evidence(self):
        cases = [replay_case("case-1")]
        report = replay_report(cases)

        without_override = score_proposal(actionable_proposal(cases, severity="high"), report)
        with_override = score_proposal(
            actionable_proposal(cases, severity="high"),
            report,
            override=RubricOverride(
                approver="reviewer",
                rationale="High-severity wallet guardrail with one available replay case.",
            ),
        )

        self.assertFalse(without_override.passed)
        self.assertTrue(without_override.manual_override_allowed)
        self.assertEqual(without_override.blocking_reasons, ("sparse_replay_evidence",))
        self.assertTrue(with_override.passed)
        self.assertTrue(with_override.manual_override_applied)
        self.assertIn("Manual override", rubric_markdown(with_override))

    def test_manual_override_does_not_allow_low_severity_sparse_evidence(self):
        cases = [replay_case("case-1")]
        report = replay_report(cases)

        result = score_proposal(
            actionable_proposal(cases, severity="low"),
            report,
            override=RubricOverride(approver="reviewer", rationale="Please publish anyway."),
        )

        self.assertFalse(result.passed)
        self.assertFalse(result.manual_override_allowed)
        self.assertFalse(result.manual_override_applied)


def grounded_runner(case: ReplayCase) -> list[ReplayFinding]:
    return [grounded_finding(case)]


def replay_report(cases: list[ReplayCase], *, proposed_runner=grounded_runner) -> ReplayReport:
    return run_replay_harness(
        current_runner=lambda item: [],
        proposed_runner=proposed_runner,
        cases=cases,
    )


def grounded_finding(case: ReplayCase) -> ReplayFinding:
    return ReplayFinding(
        case_id=case.case_id,
        summary=case.expected_finding,
        destination=case.expected_destination,
        source_url=case.source_comment_url,
    )


def actionable_proposal(
    cases: list[ReplayCase],
    *,
    severity: str = "medium",
) -> Proposal:
    source_urls = [case.source_comment_url for case in cases]
    cluster = proposal_cluster(source_urls, severity=severity)
    return Proposal(
        cluster=cluster,
        destination="agents_check",
        summary="Add a specific guardrail for the replayed wallet miss.",
        evidence_urls=source_urls,
        confidence=0.9,
        template_title=".agents/checks guardrail",
        target_artifacts=[".agents/checks/wallet-example.md"],
        sections={
            "evidence": "Replay cases show a repeated wallet review miss with source links.",
            "scope": "Apply to wallet files that match the replayed behavior boundary.",
            "validation_steps": "Run the replay harness and the focused guardrail fixture tests.",
        },
        validation_commands=["python -m unittest discover -s tests"],
        replay_cases=[case.case_id for case in cases],
    )


def vague_proposal(cases: list[ReplayCase]) -> Proposal:
    cluster = proposal_cluster([case.source_comment_url for case in cases])
    return Proposal(
        cluster=cluster,
        destination="agents_check",
        summary="Be careful.",
        evidence_urls=[],
        confidence=0.9,
        sections={"scope": "Be careful.", "validation_steps": "Think harder."},
    )


def proposal_cluster(source_urls: list[str], *, severity: str = "medium") -> Cluster:
    signals = [normalized_signal(index, url) for index, url in enumerate(source_urls)]
    return Cluster(
        theme="miss:automation:guardrail",
        signals=signals,
        area="automation",
        severity=severity,
        frequency=len(source_urls),
        rank=2.0,
        suggested_destination="agents_check",
        summary="Repeated wallet review miss.",
        source_urls=source_urls,
    )


def normalized_signal(index: int, source_url: str) -> NormalizedSignal:
    raw = RawSignal(
        kind="review_comment",
        source_id=f"review_comment:{index}",
        source_url=source_url,
        repo="squareup/wallet",
        pr_number=100 + index,
        captured_at="2026-06-03T00:00:00Z",
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
        body="",
        primary_class="miss",
        severity="medium",
        confidence=0.9,
        suggested_destination="agents_check",
    )


def replay_case(case_id: str, *, expected_severity: Severity | None = None) -> ReplayCase:
    return ReplayCase(
        case_id=case_id,
        repo="squareup/wallet",
        pr_number=123,
        pr_url="https://github.com/squareup/wallet/pull/123",
        commit_range=ReplayCommitRange(base="base", head="head", merge_commit="merge"),
        changed_files=("automation/example.py",),
        miss_class="miss",
        source_comment_url=f"https://github.com/squareup/wallet/pull/123#discussion_{case_id}",
        expected_destination="agents_check",
        expected_severity=expected_severity,
        expected_finding="Flag the historical miss.",
        summary="Historical miss summary.",
    )


if __name__ == "__main__":
    unittest.main()
