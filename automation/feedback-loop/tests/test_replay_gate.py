"""Tests for the runtime replay gate."""

from __future__ import annotations

import os
import sys
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from feedback_loop.llm import FakeLlmClient, LlmClientError  # noqa: E402
from feedback_loop.models import (  # noqa: E402
    Cluster,
    Learning,
    LearningRoute,
    NormalizedSignal,
    Proposal,
    ProposalFileChange,
    RawSignal,
    ReplayCase,
    ReplayCommitRange,
)
from feedback_loop.replay_gate import (  # noqa: E402
    run_replay_gate,
    select_replay_cases,
)


class FakeGit:
    def __init__(self, *, commits: set[str], diffs: dict[str, str] | None = None):
        self.commits = commits
        self.diffs = diffs or {}

    def commit_exists(self, sha: str) -> bool:
        return sha in self.commits

    def diff_range(self, base: str, head: str, paths=(), *, max_bytes: int = 50_000) -> str:
        return self.diffs.get(f"{base}...{head}", "diff --git a/x b/x\n+changed\n")


def replay_case(
    case_id: str,
    *,
    base: str = "aaaa111",
    head: str = "bbbb222",
    destination: str = "agents_check",
    changed_files: tuple[str, ...] = ("firmware/lib/wca/src/wca.c",),
    summary: str = "Status word handling must preserve the original error word.",
    expected_finding: str = "Preserve the original status word before retrying the command.",
    labels: tuple[str, ...] = (),
) -> ReplayCase:
    return ReplayCase(
        case_id=case_id,
        repo="squareup/wallet",
        pr_number=123,
        pr_url="https://github.com/squareup/wallet/pull/123",
        commit_range=ReplayCommitRange(base=base, head=head),
        changed_files=changed_files,
        miss_class="miss",
        source_comment_url=f"https://github.com/squareup/wallet/pull/123#discussion_{case_id}",
        expected_destination=destination,
        expected_finding=expected_finding,
        summary=summary,
        labels=labels,
    )


def learning() -> Learning:
    return Learning(
        learning_id="learn-replay",
        cluster_slug="",
        evidence_urls=("https://github.com/squareup/wallet/pull/123#discussion_r9",),
        evidence_summary="Reviewers flagged dropped status words in firmware retries.",
        agent_miss="The agent dropped the original status word before retrying.",
        human_standard="Preserve the original status word when retrying firmware commands.",
        severity="high",
        confidence=0.9,
        affected_area="firmware",
        routes=(
            LearningRoute(
                destination="agents_check",
                role="primary",
                summary="Flag dropped status words in firmware retry paths.",
            ),
        ),
    )


def proposal(destination: str = "agents_check") -> Proposal:
    signal_item = signal()
    return Proposal(
        cluster=Cluster(
            slug="miss:firmware:status-word:agents_check",
            signals=[signal_item],
            area="firmware",
            severity="high",
            frequency=2,
            rank=8.0,
            suggested_destination=destination,
            summary="Dropped status words in firmware retries.",
            source_urls=[signal_item.source_url],
        ),
        destination=destination,
        summary="Add a check that flags dropped status words in retry paths.",
        evidence_urls=[signal_item.source_url],
        confidence=0.9,
        target_artifacts=[".agents/checks/status-word.md"],
        file_changes=[
            ProposalFileChange(
                path=".agents/checks/status-word.md",
                content="Flag retry paths that drop the original status word.\n",
            )
        ],
        validation_commands=['sq agents review "main...HEAD"'],
        learning_id="learn-replay",
        route_id="llm:learn-replay:agents_check",
    )


def signal() -> NormalizedSignal:
    raw = RawSignal(
        kind="review_comment",
        source_id="review_comment:9",
        source_url="https://github.com/squareup/wallet/pull/123#discussion_r9",
        repo="squareup/wallet",
        pr_number=123,
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
        body="",
        area="firmware",
        primary_class="miss",
        severity="high",
        confidence=0.9,
        suggested_destination="agents_check",
    )


def runner_response(case_id: str, *, summary: str, anchor: str) -> dict:
    return {"findings": [{"case_id": case_id, "summary": summary, "source_url": anchor}]}


class TestCaseSelection(unittest.TestCase):
    def test_selects_resolvable_destination_matched_cases(self):
        git = FakeGit(commits={"aaaa111", "bbbb222"})
        cases = [
            replay_case("case-firmware"),
            replay_case("case-unresolvable", base="0000000"),
            replay_case("case-docs", destination="docs"),
        ]

        selection = select_replay_cases(proposal(), learning(), cases, git=git)

        self.assertEqual([case.case_id for case in selection.matched], ["case-firmware"])
        self.assertEqual(selection.unresolvable_case_ids, ("case-unresolvable",))

    def test_caps_and_orders_by_relevance_then_case_id(self):
        git = FakeGit(commits={"aaaa111", "bbbb222"})
        cases = [
            replay_case("case-d", changed_files=("app/Main.kt",), summary="unrelated"),
            replay_case("case-c"),
            replay_case("case-b"),
            replay_case("case-a"),
        ]

        selection = select_replay_cases(proposal(), learning(), cases, git=git)

        # Three firmware cases outrank the area-mismatched one and the cap is 3.
        self.assertEqual(
            [case.case_id for case in selection.matched],
            ["case-a", "case-b", "case-c"],
        )

    def test_non_mechanical_destinations_are_skipped(self):
        git = FakeGit(commits={"aaaa111", "bbbb222"})

        selection = select_replay_cases(
            proposal(destination="docs"),
            learning(),
            [replay_case("case-1", destination="docs")],
            git=git,
        )

        self.assertEqual(selection.matched, ())


class TestReplayGate(unittest.TestCase):
    def test_passes_when_runner_catches_the_miss(self):
        case = replay_case("case-1")
        client = FakeLlmClient(
            [
                runner_response(
                    "case-1",
                    summary="Retry path drops the original status word before the command retries",
                    anchor=case.source_comment_url,
                )
            ]
        )

        result = run_replay_gate(
            proposal(),
            learning(),
            client=client,
            git=FakeGit(commits={"aaaa111", "bbbb222"}),
            cases=[case],
        )

        self.assertEqual(result.status, "passed")
        self.assertEqual(result.blocking_reasons, ())
        self.assertEqual(result.matched_case_ids, ("case-1",))
        self.assertEqual(result.llm_calls, 1)
        self.assertIn("Status: PASSED", result.markdown)

    def test_fails_when_runner_misses(self):
        case = replay_case("case-1")
        client = FakeLlmClient([{"findings": []}])

        result = run_replay_gate(
            proposal(),
            learning(),
            client=client,
            git=FakeGit(commits={"aaaa111", "bbbb222"}),
            cases=[case],
        )

        self.assertEqual(result.status, "failed")
        self.assertEqual(result.blocking_reasons, ("replay_recall_below_threshold",))
        self.assertFalse(result.case_findings[0]["caught"])

    def test_runner_crash_is_a_hard_runtime_failure(self):
        case = replay_case("case-1")
        client = FakeLlmClient([LlmClientError("adapter crashed")])

        result = run_replay_gate(
            proposal(),
            learning(),
            client=client,
            git=FakeGit(commits={"aaaa111", "bbbb222"}),
            cases=[case],
        )

        self.assertEqual(result.status, "failed")
        self.assertIn("replay_runtime_failure", result.blocking_reasons)
        self.assertTrue(result.case_findings[0]["runtime_failure"])

    def test_sparse_when_no_case_is_resolvable(self):
        case = replay_case("case-1", base="0000000")
        client = FakeLlmClient([])

        result = run_replay_gate(
            proposal(),
            learning(),
            client=client,
            git=FakeGit(commits={"bbbb222"}),
            cases=[case],
        )

        self.assertEqual(result.status, "sparse")
        self.assertEqual(result.llm_calls, 0)
        self.assertEqual(result.unresolvable_case_ids, ("case-1",))
        self.assertEqual(client.requests, [])

    def test_runner_request_withholds_the_expected_answer(self):
        case = replay_case("case-1")
        client = FakeLlmClient(
            [
                runner_response(
                    "case-1",
                    summary="Retry path drops the original status word",
                    anchor=case.source_comment_url,
                )
            ]
        )

        run_replay_gate(
            proposal(),
            learning(),
            client=client,
            git=FakeGit(
                commits={"aaaa111", "bbbb222"},
                diffs={"aaaa111...bbbb222": "diff --git a/wca.c b/wca.c\n-old\n+new\n"},
            ),
            cases=[case],
        )

        request = client.requests[0]
        self.assertEqual(request["task"], "replay_check_against_historical_diff")
        case_input = request["input"]["case"]
        self.assertEqual(case_input["anchor_url"], case.source_comment_url)
        self.assertIn("diff --git", case_input["diff"])
        flattened = str(request)
        self.assertNotIn(case.expected_finding, flattened)
        self.assertNotIn(case.summary, flattened)
        self.assertNotIn("expected_finding", flattened)
        self.assertNotIn("expected_severity", flattened)


if __name__ == "__main__":
    unittest.main()
