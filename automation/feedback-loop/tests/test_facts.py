"""Tests for the deterministic facts layer."""

from __future__ import annotations

import os
import sys
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from feedback_loop.config import RunConfig  # noqa: E402
from feedback_loop.models import NormalizedSignal, RawSignal  # noqa: E402
from feedback_loop.pipeline.facts import attach_facts, check_primary_class  # noqa: E402


def normalized(
    kind: str,
    source_id: str,
    *,
    pr_number: int = 1,
    body: str = "",
    created_at: str = "",
    path: str | None = None,
    line: int | None = None,
    author_association: str = "",
    is_bot: bool = False,
    raw: dict | None = None,
) -> NormalizedSignal:
    raw_signal = RawSignal(
        kind=kind,
        source_id=source_id,
        source_url=f"https://github.com/squareup/wallet/pull/{pr_number}#{source_id}",
        repo="squareup/wallet",
        pr_number=pr_number,
        captured_at="2026-06-09T00:00:00Z",
        created_at=created_at,
        body=body,
        path=path,
        line=line,
        is_bot=is_bot,
        raw=raw or {},
    )
    return NormalizedSignal(
        raw=raw_signal,
        kind=kind,
        source=kind,
        source_id=source_id,
        source_url=raw_signal.source_url,
        repo=raw_signal.repo,
        pr_number=pr_number,
        captured_at=raw_signal.captured_at,
        harvest_version="test",
        body=body,
        raw_metadata=dict(raw or {}),
        author_association=author_association,
        created_at=created_at,
        path=path,
        line=line,
        is_bot=is_bot,
        area="app" if (path or "").startswith("app/") else "",
    )


def pr_metadata(pr_number: int = 1) -> NormalizedSignal:
    return normalized(
        "pr_metadata",
        f"pr:squareup/wallet#{pr_number}",
        pr_number=pr_number,
        raw={
            "shas": {"base": "a" * 40, "head": "b" * 40, "merge_commit": "c" * 40},
            "timestamps": {"merged_at": "2026-06-08T12:00:00Z"},
        },
    )


class TestAttachFacts(unittest.TestCase):
    def test_thread_timeline_and_diff_facts(self):
        comment = normalized(
            "review_comment",
            "review_comment:1",
            body="Please add a regression test.",
            created_at="2026-06-08T01:00:00Z",
            path="app/Feature.kt",
            line=12,
            author_association="MEMBER",
            raw={"id": "900", "thread_resolved": True},
        )
        reply = normalized(
            "review_comment",
            "review_comment:2",
            body="Done, added the test.",
            created_at="2026-06-08T02:00:00Z",
            raw={"in_reply_to_id": "900"},
        )
        earlier_reply = normalized(
            "review_comment",
            "review_comment:0",
            body="Earlier unrelated comment.",
            created_at="2026-06-07T23:00:00Z",
            raw={"in_reply_to_id": "900"},
        )
        commit = normalized(
            "commit",
            "commit:abc",
            body="add regression test",
            created_at="2026-06-08T03:00:00Z",
            raw={"sha": "abc123"},
        )
        hunk = normalized(
            "diff_hunk",
            "hunk:1",
            path="app/Feature.kt",
            raw={"new_start": 10, "new_count": 5},
        )
        changed = normalized("changed_file", "file:1", path="app/Feature.kt")

        result = attach_facts(
            RunConfig(),
            [pr_metadata(), comment, reply, earlier_reply, commit, hunk, changed],
        )

        facts = next(
            signal for signal in result.signals if signal.source_id == "review_comment:1"
        ).facts
        assert facts is not None
        self.assertTrue(facts.thread_resolved)
        self.assertEqual(facts.later_reply_source_ids, ("review_comment:2",))
        self.assertEqual(facts.later_commit_source_ids, ("commit:abc",))
        self.assertTrue(facts.path_in_diff)
        self.assertTrue(facts.line_in_changed_hunk)
        self.assertTrue(facts.author_trusted)
        self.assertFalse(facts.author_is_bot)

    def test_reviewed_earlier_head_detection(self):
        bot_review = normalized(
            "bot_review",
            "bot_review:1",
            body="Automated finding.",
            created_at="2026-06-08T01:00:00Z",
            is_bot=True,
            raw={"reviewed_head_sha": "d" * 40},
        )

        result = attach_facts(RunConfig(), [pr_metadata(), bot_review])

        facts = result.signals[1].facts
        assert facts is not None
        self.assertTrue(facts.reviewed_earlier_head)
        self.assertEqual(facts.final_head_sha, "b" * 40)
        self.assertTrue(facts.author_trusted)

    def test_check_signals_stay_deterministically_classified(self):
        ci = normalized(
            "check",
            "check:ci",
            raw={"source": "check_run", "name": "android-build", "conclusion": "failure"},
        )
        validation = normalized(
            "check",
            "check:validation",
            raw={"source": "workflow_run", "name": "ai-context-check", "conclusion": "failure"},
        )

        result = attach_facts(RunConfig(), [ci, validation])

        self.assertEqual(check_primary_class(ci), "ci_failure")
        self.assertEqual(result.signals[0].primary_class, "ci_failure")
        self.assertEqual(result.signals[0].suggested_destination, "test_or_linter")
        self.assertEqual(result.signals[1].primary_class, "validation_failure")
        self.assertIn("validation", result.signals[1].secondary_tags)

    def test_pr_facts_bundle_includes_commits_and_failed_checks(self):
        commit = normalized(
            "commit",
            "commit:abc",
            body="fix: preserve status word\n\ndetails",
            created_at="2026-06-08T03:00:00Z",
            raw={"sha": "abc123"},
        )
        check = normalized(
            "check",
            "check:ci",
            created_at="2026-06-08T04:00:00Z",
            raw={"source": "check_run", "name": "android-build", "conclusion": "failure"},
        )
        changed = normalized("changed_file", "file:1", path="app/Feature.kt")

        result = attach_facts(RunConfig(), [pr_metadata(), commit, check, changed])

        facts = result.pr_facts[1]
        self.assertEqual(facts.base_sha, "a" * 40)
        self.assertEqual(facts.head_sha, "b" * 40)
        self.assertEqual(facts.changed_paths, ("app/Feature.kt",))
        self.assertEqual(facts.commits[0].message_first_line, "fix: preserve status word")
        self.assertEqual(facts.failed_checks[0].name, "android-build")
        self.assertEqual(facts.failed_checks[0].primary_class, "ci_failure")


if __name__ == "__main__":
    unittest.main()
