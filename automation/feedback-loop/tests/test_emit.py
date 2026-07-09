"""Tests for BKW-51 draft PR emission."""

from __future__ import annotations

import os
import sys
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from feedback_loop.config import RunConfig  # noqa: E402
from feedback_loop.eval_gate import evaluate_proposal, mark_pr_ready  # noqa: E402
from feedback_loop.linear_control import BUILDERBOT_APPROVAL_LABEL  # noqa: E402
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
from feedback_loop.pipeline.emit import (  # noqa: E402
    AI_CONTEXT_COMMANDS,
    build_draft_pr_plan,
    emit,
)
from feedback_loop.replay import run_replay_harness  # noqa: E402


class TestEmit(unittest.TestCase):
    def test_dry_run_returns_draft_plan_without_builderbot_trigger_label(self):
        ready = ready_proposal(
            [ProposalFileChange(path=".agents/checks/example.md", content="check body\n")]
        )

        results = emit(RunConfig(dry_run=True), [ready])

        self.assertEqual(len(results), 1)
        self.assertEqual(results[0].linear_issue_url, "")
        self.assertTrue(results[0].draft_pr.draft)
        self.assertNotIn(BUILDERBOT_APPROVAL_LABEL, results[0].cluster_issue.labels)
        self.assertIn(".agents/checks/example.md", results[0].draft_pr.body)
        self.assertIn("Status: PASS", results[0].draft_pr.body)
        self.assertIn("## Reviewer checklist", results[0].draft_pr.body)
        self.assertIn("Evidence quality", results[0].draft_pr.body)
        self.assertIn("https://github.com/squareup/wallet/pull/123", results[0].draft_pr.body)

    def test_execute_creates_linear_issue_with_builderbot_trigger_label(self):
        linear_plans = []

        def linear_writer(plan):
            linear_plans.append(plan)
            return "https://linear.app/squareup/issue/BKW-999/example"

        ready = ready_proposal(
            [ProposalFileChange(path=".agents/checks/example.md", content="check body\n")]
        )

        results = emit(
            RunConfig(dry_run=False),
            [ready],
            linear_writer=linear_writer,
        )

        self.assertEqual(len(linear_plans), 1)
        self.assertEqual(
            results[0].linear_issue_url,
            "https://linear.app/squareup/issue/BKW-999/example",
        )
        self.assertEqual(results[0].cluster_issue.status, "eval_passed")
        self.assertEqual(results[0].cluster_issue.linear_state, "In Progress")
        self.assertIn(BUILDERBOT_APPROVAL_LABEL, results[0].cluster_issue.labels)
        self.assertIn(BUILDERBOT_APPROVAL_LABEL, results[0].cluster_issue.description)
        self.assertIn("## Builderbot implementation context", linear_plans[0].description)
        self.assertIn("Draft PR title: Feedback loop: agents check guardrail", linear_plans[0].description)
        self.assertIn("## Reviewer checklist", linear_plans[0].description)
        self.assertIn("- Path: `.agents/checks/example.md`", linear_plans[0].description)
        self.assertIn("- Mode: `create_or_update`", linear_plans[0].description)
        self.assertIn("check body", linear_plans[0].description)

    def test_execute_requires_linear_writer(self):
        ready = ready_proposal(
            [ProposalFileChange(path=".agents/checks/example.md", content="check body\n")]
        )

        with self.assertRaisesRegex(NotImplementedError, "Linear cluster issue writer"):
            emit(RunConfig(dry_run=False), [ready])

    def test_ai_context_commands_are_added_for_ai_source_changes(self):
        ready = ready_proposal(
            [ProposalFileChange(path=".ai/AGENTS.md", content="agent rule\n")],
            destination="ai_agents_md",
        )

        plan = build_draft_pr_plan(ready)
        results = emit(RunConfig(dry_run=True), [ready])

        for command in AI_CONTEXT_COMMANDS:
            self.assertIn(command, plan.validation_commands)
            self.assertIn(command, plan.body)
            self.assertIn(command, results[0].cluster_issue.description)

    def test_execute_checks_pr_policy_before_linear_write(self):
        linear_plans = []
        ready = ready_proposal(
            [
                ProposalFileChange(path=".agents/checks/example.md", content="check body\n"),
                ProposalFileChange(path="docs/docs/example.md", content="docs body\n"),
            ]
        )

        with self.assertRaisesRegex(RuntimeError, "one-change policy"):
            emit(
                RunConfig(dry_run=False),
                [ready],
                linear_writer=lambda plan: linear_plans.append(plan) or "",
            )
        self.assertEqual(linear_plans, [])

    def test_execute_preflights_all_proposals_before_linear_write(self):
        linear_plans = []
        valid = ready_proposal(
            [ProposalFileChange(path=".agents/checks/example.md", content="check body\n")]
        )
        invalid = ready_proposal(
            [ProposalFileChange(path="../outside.md", content="bad\n")]
        )

        with self.assertRaisesRegex(RuntimeError, "one-change policy"):
            emit(
                RunConfig(dry_run=False),
                [valid, invalid],
                linear_writer=lambda plan: linear_plans.append(plan) or "",
            )
        self.assertEqual(linear_plans, [])

    def test_rejects_invalid_file_change_paths_before_linear_write(self):
        ready = ready_proposal(
            [ProposalFileChange(path="../outside.md", content="bad\n")]
        )

        with self.assertRaisesRegex(RuntimeError, "one-change policy"):
            emit(
                RunConfig(dry_run=False),
                [ready],
                linear_writer=lambda plan: "",
            )


def ready_proposal(
    file_changes: list[ProposalFileChange],
    *,
    destination: str = "agents_check",
) -> Proposal:
    cases = [replay_case("case-1"), replay_case("case-2")]
    proposal = actionable_proposal(cases, file_changes=file_changes, destination=destination)
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


def actionable_proposal(
    cases: list[ReplayCase],
    *,
    file_changes: list[ProposalFileChange],
    destination: str = "agents_check",
) -> Proposal:
    source_urls = [case.source_comment_url for case in cases]
    target_artifacts = [change.path for change in file_changes] or [".agents/checks/example.md"]
    return Proposal(
        cluster=proposal_cluster(source_urls, destination=destination),
        destination=destination,
        summary="Add a specific guardrail for the replayed wallet miss.",
        evidence_urls=source_urls,
        confidence=0.9,
        template_title=".agents/checks guardrail",
        target_artifacts=target_artifacts,
        file_changes=file_changes,
        sections={
            "evidence": "Replay cases show a repeated wallet review miss with source links.",
            "scope": "Apply to wallet files that match the replayed behavior boundary.",
            "validation_steps": "Run the replay harness and focused guardrail fixture tests.",
            "reviewer_instructions": "Review the guardrail scope and replay rubric results.",
        },
        validation_commands=["python -m unittest discover -s tests"],
        replay_cases=[case.case_id for case in cases],
    )


def proposal_cluster(source_urls: list[str], *, destination: str = "agents_check") -> Cluster:
    signals = [
        normalized_signal(index, url, destination=destination)
        for index, url in enumerate(source_urls)
    ]
    return Cluster(
        theme="miss:automation:guardrail",
        signals=signals,
        area="automation",
        severity="medium",
        frequency=len(source_urls),
        rank=2.0,
        suggested_destination=destination,
        summary="Repeated wallet review miss.",
        source_urls=source_urls,
    )


def normalized_signal(index: int, source_url: str, *, destination: str) -> NormalizedSignal:
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
        suggested_destination=destination,
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
