"""Tests for the proposal eval gate."""

from __future__ import annotations

from dataclasses import replace
import os
import sys
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from feedback_loop.config import RunConfig  # noqa: E402
from feedback_loop.eval_gate import (  # noqa: E402
    ProposalEvalBlocked,
    evaluate_proposal,
    mark_pr_ready,
)
from feedback_loop.models import (  # noqa: E402
    Cluster,
    NormalizedSignal,
    Proposal,
    ProposalFileChange,
    RawSignal,
    ReplayCase,
    ReplayCommitRange,
    ReplayFinding,
    ReplayReport,
)
from feedback_loop.pipeline import emit  # noqa: E402
from feedback_loop.replay import run_replay_harness  # noqa: E402
from feedback_loop.rubric import RubricOverride  # noqa: E402


class TestEvalGate(unittest.TestCase):
    def test_passed_eval_records_artifact(self):
        cases = [replay_case("case-1"), replay_case("case-2")]

        gated = evaluate_proposal(actionable_proposal(cases), replay_report(cases))

        self.assertTrue(gated.eval_passed)
        self.assertEqual(gated.eval_state, "eval_passed")
        self.assertIsNotNone(gated.eval_artifact)
        self.assertEqual(gated.eval_artifact.failure_destination, "none")
        self.assertIn("Status: PASS", gated.eval_artifact.rubric_markdown)

    def test_failed_eval_records_triage_destination_and_blocks_pr_ready(self):
        cases = [replay_case("case-1"), replay_case("case-2")]

        gated = evaluate_proposal(vague_proposal(cases), replay_report(cases))

        self.assertFalse(gated.eval_passed)
        self.assertEqual(gated.eval_state, "eval_failed")
        self.assertEqual(gated.eval_artifact.failure_destination, "triage")
        self.assertIn("proposal_not_actionable", gated.eval_artifact.blocking_reasons)
        with self.assertRaisesRegex(ProposalEvalBlocked, "pass eval"):
            mark_pr_ready(gated)

    def test_sparse_evidence_goes_to_research_until_high_severity_override(self):
        cases = [replay_case("case-1")]
        proposal = actionable_proposal(cases, severity="high")
        report = replay_report(cases)

        failed = evaluate_proposal(proposal, report)
        overridden = evaluate_proposal(
            proposal,
            report,
            override=RubricOverride(
                approver="reviewer",
                rationale="High-severity replay case is sparse but actionable.",
            ),
        )
        ready = mark_pr_ready(overridden, future_pr_url="https://github.com/squareup/wallet/pull/1")

        self.assertFalse(failed.eval_passed)
        self.assertEqual(failed.eval_artifact.failure_destination, "research")
        self.assertTrue(overridden.eval_passed)
        self.assertIn("reviewer:", overridden.eval_artifact.manual_override)
        self.assertEqual(ready.eval_state, "pr_ready")
        self.assertEqual(ready.eval_artifact.future_pr_url, "https://github.com/squareup/wallet/pull/1")

    def test_mark_pr_ready_rejects_fabricated_eval_passed_flags(self):
        cases = [replay_case("case-1"), replay_case("case-2")]
        forged = replace(actionable_proposal(cases), eval_state="eval_passed", eval_passed=True)

        with self.assertRaisesRegex(ProposalEvalBlocked, "eval-passed artifact"):
            mark_pr_ready(forged)

    def test_emit_dry_run_refuses_unready_proposals(self):
        cases = [replay_case("case-1"), replay_case("case-2")]
        proposal = actionable_proposal(cases)

        with self.assertRaisesRegex(ProposalEvalBlocked, "PR-ready"):
            emit.emit(RunConfig(dry_run=True), [proposal])

    def test_emit_execute_refuses_unready_proposals_before_write_scaffold(self):
        cases = [replay_case("case-1"), replay_case("case-2")]
        proposal = actionable_proposal(cases)

        with self.assertRaisesRegex(ProposalEvalBlocked, "PR-ready"):
            emit.emit(RunConfig(dry_run=False), [proposal])

    def test_emit_execute_refuses_forged_pr_ready_flags(self):
        cases = [replay_case("case-1"), replay_case("case-2")]
        forged = replace(actionable_proposal(cases), eval_state="pr_ready", eval_passed=True)

        with self.assertRaisesRegex(ProposalEvalBlocked, "PR-ready"):
            emit.emit(RunConfig(dry_run=False), [forged])

    def test_emit_dry_run_returns_plan_for_pr_ready_proposals(self):
        cases = [replay_case("case-1"), replay_case("case-2")]
        gated = evaluate_proposal(actionable_proposal(cases), replay_report(cases))
        ready = mark_pr_ready(gated)

        results = emit.emit(RunConfig(dry_run=True), [ready])

        self.assertEqual(len(results), 1)
        self.assertTrue(results[0].draft_pr.draft)
        self.assertIn("Status: PASS", results[0].draft_pr.body)


def replay_report(cases: list[ReplayCase]) -> ReplayReport:
    return run_replay_harness(
        current_runner=lambda item: [],
        proposed_runner=lambda item: [grounded_finding(item)],
        cases=cases,
    )


def grounded_finding(case: ReplayCase) -> ReplayFinding:
    return ReplayFinding(
        case_id=case.case_id,
        summary=case.expected_finding,
        destination=case.expected_destination,
        source_url=case.source_comment_url,
    )


def actionable_proposal(cases: list[ReplayCase], *, severity: str = "medium") -> Proposal:
    source_urls = [case.source_comment_url for case in cases]
    return Proposal(
        cluster=proposal_cluster(source_urls, severity=severity),
        destination="agents_check",
        summary="Add a specific guardrail for the replayed wallet miss.",
        evidence_urls=source_urls,
        confidence=0.9,
        template_title=".agents/checks guardrail",
        target_artifacts=[".agents/checks/wallet-example.md"],
        sections={
            "evidence": "Replay cases show a repeated wallet review miss with source links.",
            "scope": "Apply to wallet files that match the replayed behavior boundary.",
            "validation_steps": "Run the replay harness and focused guardrail fixture tests.",
        },
        validation_commands=["python -m unittest discover -s tests"],
        replay_cases=[case.case_id for case in cases],
        file_changes=[
            ProposalFileChange(
                path=".agents/checks/wallet-example.md",
                content="Guardrail body.\n",
            )
        ],
    )


def vague_proposal(cases: list[ReplayCase]) -> Proposal:
    source_urls = [case.source_comment_url for case in cases]
    return Proposal(
        cluster=proposal_cluster(source_urls),
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


def replay_case(case_id: str) -> ReplayCase:
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
        expected_finding="Flag the historical miss.",
        summary="Historical miss summary.",
    )


if __name__ == "__main__":
    unittest.main()
