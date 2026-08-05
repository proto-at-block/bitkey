"""Tests for BKW-79 Linear cluster issue planning."""

from __future__ import annotations

import os
import sys
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from feedback_loop.eval_gate import evaluate_proposal, mark_pr_ready  # noqa: E402
from feedback_loop.linear_control import (  # noqa: E402
    BUILDERBOT_APPROVAL_LABEL,
    build_cluster_issue_plan,
    create_or_update_cluster_issue,
    linear_state_for_cluster_status,
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
)
from feedback_loop.replay import run_replay_harness  # noqa: E402


class TestLinearControl(unittest.TestCase):
    def test_builds_unassigned_cluster_issue_with_links_and_eval_artifact(self):
        proposal = ready_proposal()

        plan = build_cluster_issue_plan(
            proposal,
            pr_url="https://github.com/squareup/wallet/pull/999",
            status="pr_open",
        )

        self.assertTrue(plan.idempotency_key.startswith("feedback-loop:"))
        self.assertIn("Feedback cluster:", plan.title)
        self.assertEqual(plan.team_key, "BKW")
        self.assertEqual(plan.project_name, "Linear-driven code engine")
        self.assertEqual(plan.assignee, None)
        self.assertEqual(plan.linear_state, "In Review")
        self.assertIn("https://github.com/squareup/wallet/pull/123", plan.description)
        self.assertIn("https://github.com/squareup/wallet/pull/999", plan.description)
        self.assertIn("Status: PASS", plan.description)
        self.assertNotIn(BUILDERBOT_APPROVAL_LABEL, plan.labels)

    def test_status_mapping_covers_feedback_loop_lifecycle(self):
        self.assertEqual(linear_state_for_cluster_status("harvested"), "Todo")
        self.assertEqual(linear_state_for_cluster_status("classified"), "Todo")
        self.assertEqual(linear_state_for_cluster_status("needs_triage"), "Todo")
        self.assertEqual(linear_state_for_cluster_status("proposal_drafted"), "In Progress")
        self.assertEqual(linear_state_for_cluster_status("eval_passed"), "In Progress")
        self.assertEqual(linear_state_for_cluster_status("pr_open"), "In Review")
        self.assertEqual(linear_state_for_cluster_status("adopted"), "Done")
        self.assertEqual(linear_state_for_cluster_status("rejected"), "Canceled")

    def test_create_or_update_uses_injected_idempotent_writer(self):
        proposal = ready_proposal()
        plan = build_cluster_issue_plan(proposal)
        seen = []

        def writer(item):
            seen.append(item.idempotency_key)
            return "https://linear.app/squareup/issue/BKW-999/example"

        result = create_or_update_cluster_issue(plan, writer)

        self.assertEqual(seen, [plan.idempotency_key])
        self.assertEqual(result.issue_url, "https://linear.app/squareup/issue/BKW-999/example")

    def test_idempotency_key_is_stable_for_same_cluster(self):
        proposal = ready_proposal()

        first = build_cluster_issue_plan(proposal)
        second = build_cluster_issue_plan(proposal)

        self.assertEqual(first.idempotency_key, second.idempotency_key)

    def test_builderbot_trigger_label_is_opt_in(self):
        plan = build_cluster_issue_plan(
            ready_proposal(),
            trigger_builderbot=True,
            validation_commands=("python -m unittest discover -s tests",),
        )

        self.assertIn(BUILDERBOT_APPROVAL_LABEL, plan.labels)
        self.assertIn(BUILDERBOT_APPROVAL_LABEL, plan.description)
        self.assertIn("Builderbot code engine", plan.description)
        self.assertIn("python -m unittest discover -s tests", plan.description)

    def test_builderbot_context_includes_bounded_draft_and_file_content(self):
        proposal = ready_proposal()
        proposal.file_changes = [
            ProposalFileChange(
                path=".agents/checks/example.md",
                content="```fenced\n" + ("x" * 2100),
            )
        ]

        plan = build_cluster_issue_plan(
            proposal,
            trigger_builderbot=True,
            draft_pr_title="Feedback loop: example",
            draft_pr_body="## Summary\nUse this plan.",
        )

        self.assertIn("Draft PR title: Feedback loop: example", plan.description)
        self.assertIn("## Summary\nUse this plan.", plan.description)
        self.assertIn("- Path: `.agents/checks/example.md`", plan.description)
        self.assertIn("```fenced", plan.description)
        self.assertIn("truncated", plan.description)
        self.assertNotIn("x" * 2100, plan.description)


def ready_proposal() -> Proposal:
    cases = [replay_case("case-1"), replay_case("case-2")]
    proposal = actionable_proposal(cases)
    report = run_replay_harness(
        current_runner=lambda item: [],
        proposed_runner=lambda item: [grounded_finding(item)],
        cases=cases,
    )
    return mark_pr_ready(evaluate_proposal(proposal, report))


def grounded_finding(case: ReplayCase) -> ReplayFinding:
    return ReplayFinding(
        case_id=case.case_id,
        summary="Proposed guidance flags the replay miss.",
        destination=case.expected_destination,
        source_url=case.source_comment_url,
    )


def actionable_proposal(cases: list[ReplayCase]) -> Proposal:
    source_urls = [case.source_comment_url for case in cases]
    return Proposal(
        cluster=proposal_cluster(source_urls),
        destination="agents_check",
        summary="Add a specific guardrail for the replayed wallet miss.",
        evidence_urls=source_urls,
        confidence=0.9,
        template_title=".agents/checks guardrail",
        target_artifacts=[".agents/checks/example.md"],
        sections={
            "evidence": "Replay cases show a repeated wallet review miss with source links.",
            "scope": "Apply to wallet files that match the replayed behavior boundary.",
            "validation_steps": "Run the replay harness and focused guardrail fixture tests.",
            "reviewer_instructions": "Review the guardrail scope and replay rubric results.",
        },
        validation_commands=["python -m unittest discover -s tests"],
        replay_cases=[case.case_id for case in cases],
    )


def proposal_cluster(source_urls: list[str]) -> Cluster:
    signals = [normalized_signal(index, url) for index, url in enumerate(source_urls)]
    return Cluster(
        theme="miss:automation:guardrail",
        signals=signals,
        area="automation",
        severity="medium",
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
