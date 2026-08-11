"""Tests for replay-corpus suggestion artifacts."""

from __future__ import annotations

import os
import sys
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from feedback_loop.corpus_suggest import pr_facts_from_raw, suggest_replay_cases  # noqa: E402
from feedback_loop.models import (  # noqa: E402
    Cluster,
    Learning,
    LearningRoute,
    NormalizedSignal,
    Proposal,
    ProposalFileChange,
    RawSignal,
)
from feedback_loop.replay import _replay_case_from_json  # noqa: E402


def raw_pr_metadata(pr_number: int = 123) -> RawSignal:
    return RawSignal(
        kind="pr_metadata",
        source_id=f"pr:squareup/wallet#{pr_number}",
        source_url=f"https://github.com/squareup/wallet/pull/{pr_number}",
        repo="squareup/wallet",
        pr_number=pr_number,
        captured_at="2026-06-09T00:00:00Z",
        raw={
            "shas": {"base": "a" * 40, "head": "b" * 40, "merge_commit": "c" * 40},
            "timestamps": {"merged_at": "2026-06-08T12:00:00Z"},
        },
    )


def raw_changed_file(path: str, pr_number: int = 123) -> RawSignal:
    return RawSignal(
        kind="changed_file",
        source_id=f"file:squareup/wallet#{pr_number}:{path}",
        source_url=f"https://github.com/squareup/wallet/pull/{pr_number}",
        repo="squareup/wallet",
        pr_number=pr_number,
        captured_at="2026-06-09T00:00:00Z",
        path=path,
    )


def learning(learning_id: str = "route-coverage", pr_number: int = 123) -> Learning:
    return Learning(
        learning_id=learning_id,
        cluster_slug="miss:automation:route:agents_check",
        evidence_urls=(
            f"https://github.com/squareup/wallet/pull/{pr_number}#discussion_r1",
        ),
        evidence_summary="Reviewers required route plans to be source-grounded.",
        agent_miss="The agent handed off a route plan without source grounding.",
        human_standard="Route plans must be source-grounded before generated handoff.",
        severity="high",
        confidence=0.9,
        affected_area="automation",
        routes=(
            LearningRoute(
                destination="agents_check",
                role="primary",
                summary="Flag ungrounded route plans.",
            ),
        ),
    )


def proposal(learning_id: str = "route-coverage", pr_number: int = 123) -> Proposal:
    signal_item = signal(pr_number)
    return Proposal(
        cluster=Cluster(
            slug="miss:automation:route:agents_check",
            signals=[signal_item],
            area="automation",
            severity="high",
            frequency=2,
            rank=8.0,
            suggested_destination="agents_check",
            summary="Ungrounded route plans.",
            source_urls=[signal_item.source_url],
        ),
        destination="agents_check",
        summary="Add an agents check for ungrounded route plans.",
        evidence_urls=[signal_item.source_url],
        confidence=0.9,
        file_changes=[
            ProposalFileChange(path=".agents/checks/route.md", content="check\n")
        ],
        learning_id=learning_id,
        route_id=f"llm:{learning_id}:agents_check",
        route_role="primary",
    )


def signal(pr_number: int) -> NormalizedSignal:
    raw = RawSignal(
        kind="review_comment",
        source_id="review_comment:1",
        source_url=f"https://github.com/squareup/wallet/pull/{pr_number}#discussion_r1",
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
        body="",
        primary_class="miss",
    )


class TestPrFactsFromRaw(unittest.TestCase):
    def test_extracts_shas_and_changed_paths(self):
        facts = pr_facts_from_raw(
            [
                raw_pr_metadata(),
                raw_changed_file("app/src/Main.kt"),
                raw_changed_file("app/src/test/MainTest.kt"),
                raw_changed_file("app/src/Main.kt"),
            ]
        )

        self.assertEqual(set(facts), {123})
        self.assertEqual(facts[123].base_sha, "a" * 40)
        self.assertEqual(facts[123].head_sha, "b" * 40)
        self.assertEqual(facts[123].merge_sha, "c" * 40)
        self.assertEqual(facts[123].merged_at, "2026-06-08T12:00:00Z")
        self.assertEqual(
            facts[123].changed_paths,
            ("app/src/Main.kt", "app/src/test/MainTest.kt"),
        )


class TestSuggestReplayCases(unittest.TestCase):
    def test_suggestions_round_trip_as_valid_replay_cases(self):
        facts = pr_facts_from_raw([raw_pr_metadata(), raw_changed_file("app/src/Main.kt")])

        suggestions = suggest_replay_cases(
            learnings=[learning()],
            proposals=[proposal()],
            pr_facts_by_number=facts,
        )

        self.assertEqual(len(suggestions), 1)
        suggestion = suggestions[0]
        self.assertEqual(suggestion["id"], "wallet-pr-123-route-coverage")
        self.assertEqual(suggestion["expected_destination"], "agents_check")
        self.assertIn("suggested_by_run", suggestion)
        stripped = {key: value for key, value in suggestion.items() if key != "suggested_by_run"}
        case = _replay_case_from_json(stripped, index=0)
        self.assertEqual(case.commit_range.base, "a" * 40)
        self.assertEqual(case.expected_finding, learning().human_standard)

    def test_dedupes_against_existing_corpus(self):
        facts = pr_facts_from_raw([raw_pr_metadata(), raw_changed_file("app/src/Main.kt")])

        by_id = suggest_replay_cases(
            learnings=[learning()],
            proposals=[proposal()],
            pr_facts_by_number=facts,
            existing_case_ids=frozenset({"wallet-pr-123-route-coverage"}),
        )
        by_pr = suggest_replay_cases(
            learnings=[learning()],
            proposals=[proposal()],
            pr_facts_by_number=facts,
            existing_pr_numbers=frozenset({123}),
        )

        self.assertEqual(by_id, [])
        self.assertEqual(by_pr, [])

    def test_low_severity_and_factless_learnings_are_skipped(self):
        facts = pr_facts_from_raw([raw_pr_metadata(), raw_changed_file("app/src/Main.kt")])
        low = Learning(
            learning_id="low-sev",
            cluster_slug="",
            evidence_urls=("https://github.com/squareup/wallet/pull/123#discussion_r1",),
            evidence_summary="Minor style theme.",
            agent_miss="The agent used inconsistent naming in one file.",
            human_standard="Follow the naming convention in the style guide.",
            severity="low",
            confidence=0.7,
            affected_area="app",
            routes=(
                LearningRoute(destination="docs", role="primary", summary="Document it."),
            ),
        )
        unharvested = learning(learning_id="other-pr", pr_number=999)

        self.assertEqual(
            suggest_replay_cases(
                learnings=[low],
                proposals=[proposal(learning_id="low-sev")],
                pr_facts_by_number=facts,
            ),
            [],
        )
        self.assertEqual(
            suggest_replay_cases(
                learnings=[unharvested],
                proposals=[proposal(learning_id="other-pr", pr_number=999)],
                pr_facts_by_number=facts,
            ),
            [],
        )


if __name__ == "__main__":
    unittest.main()
