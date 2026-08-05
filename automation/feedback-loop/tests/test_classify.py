"""Tests for BKW-84 deterministic comment-to-change correlation."""

from __future__ import annotations

import os
import sys
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from feedback_loop.config import RunConfig  # noqa: E402
from feedback_loop.models import Correlation, NormalizedSignal, RawSignal  # noqa: E402
from feedback_loop.pipeline.classify import classify  # noqa: E402
from feedback_loop.pipeline.normalize import normalize  # noqa: E402


def raw_signal(
    kind: str,
    source_id: str,
    *,
    body: str = "",
    created_at: str = "",
    pr_number: int = 123,
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


def correlated(*signals: RawSignal) -> list[NormalizedSignal]:
    cfg = RunConfig(harvest_version="test")
    return classify(cfg, normalize(cfg, list(signals)))


def require_correlation(signal: NormalizedSignal) -> Correlation:
    correlation = signal.correlation
    if correlation is None:
        raise AssertionError("expected signal to have correlation metadata")
    return correlation


class TestClassifyCorrelation(unittest.TestCase):
    def test_correlates_review_comment_to_changed_path_line_and_later_commit(self):
        feedback = raw_signal(
            "review_comment",
            "review_comment:1",
            body="Please add a regression test here.",
            created_at="2026-05-04T01:00:00Z",
            path="automation/feedback-loop/feedback_loop/pipeline/harvest.py",
            line=12,
        )
        changed_file = raw_signal(
            "changed_file",
            "file:harvest",
            path="automation/feedback-loop/feedback_loop/pipeline/harvest.py",
        )
        diff_hunk = raw_signal(
            "diff_hunk",
            "hunk:harvest:10",
            path="automation/feedback-loop/feedback_loop/pipeline/harvest.py",
            raw={"new_start": 10, "new_count": 5},
        )
        commit = raw_signal(
            "commit",
            "commit:abc",
            body="fix review comment with regression test",
            created_at="2026-05-04T02:00:00Z",
        )

        item = correlated(feedback, changed_file, diff_hunk, commit)[0]

        correlation = require_correlation(item)
        self.assertTrue(correlation.likely_miss)
        self.assertEqual(item.confidence, correlation.confidence)
        self.assertIn("likely_miss_correlation", item.secondary_tags)
        self.assertIn("file:harvest", item.evidence_ids)
        self.assertIn("hunk:harvest:10", item.evidence_ids)
        self.assertIn("commit:abc", item.evidence_ids)
        self.assertIn("feedback line falls inside a harvested diff hunk", item.rationale)
        self.assertEqual(item.primary_class, "miss")
        self.assertEqual(item.severity, "high")
        self.assertEqual(item.suggested_destination, "test_or_linter")
        self.assertFalse(item.manual_triage)
        self.assertEqual(item.area, "automation")

    def test_correlates_comment_without_path_to_later_reply_and_commit(self):
        feedback = raw_signal(
            "issue_comment",
            "issue_comment:1",
            body="Can we validate this edge case?",
            created_at="2026-05-04T01:00:00Z",
            raw={"thread_id": "discussion:1"},
        )
        reply = raw_signal(
            "issue_comment",
            "issue_comment:2",
            body="Fixed, added coverage.",
            created_at="2026-05-04T01:30:00Z",
            raw={"thread_id": "discussion:1"},
        )
        commit = raw_signal(
            "commit",
            "commit:def",
            body="address validation coverage",
            created_at="2026-05-04T02:00:00Z",
        )
        check = raw_signal(
            "check",
            "check:buildkite",
            body="buildkite/wallet/pr | failure",
            created_at="2026-05-04T02:15:00Z",
        )

        item = correlated(feedback, reply, commit, check)[0]

        correlation = require_correlation(item)
        self.assertTrue(correlation.likely_miss)
        self.assertIn("issue_comment:2", item.evidence_ids)
        self.assertIn("commit:def", item.evidence_ids)
        self.assertIn("check:buildkite", item.evidence_ids)
        self.assertIn("later reply text indicates the feedback was addressed", item.rationale)
        self.assertIn("failed CI/check signal exists after the feedback timestamp", item.rationale)
        self.assertEqual(item.primary_class, "miss")
        self.assertEqual(item.suggested_destination, "test_or_linter")

    def test_fixed_inline_reply_is_parent_evidence_not_new_miss_candidate(self):
        feedback = raw_signal(
            "review_comment",
            "review_comment:1",
            body="Please add coverage for this branch.",
            created_at="2026-05-04T01:00:00Z",
            path="automation/feedback-loop/feedback_loop/pipeline/classify.py",
            line=12,
            raw={"id": "1"},
        )
        reply = raw_signal(
            "review_comment",
            "review_comment:2",
            body="Fixed, added coverage.",
            created_at="2026-05-04T01:30:00Z",
            path="automation/feedback-loop/feedback_loop/pipeline/classify.py",
            line=12,
            raw={"id": "2", "in_reply_to_id": "1"},
        )
        changed_file = raw_signal(
            "changed_file",
            "file:classify",
            path="automation/feedback-loop/feedback_loop/pipeline/classify.py",
        )
        diff_hunk = raw_signal(
            "diff_hunk",
            "hunk:classify:10",
            path="automation/feedback-loop/feedback_loop/pipeline/classify.py",
            raw={"new_start": 10, "new_count": 5},
        )
        commit = raw_signal(
            "commit",
            "commit:coverage",
            body="fix coverage branch",
            created_at="2026-05-04T02:00:00Z",
        )

        classified = correlated(feedback, reply, changed_file, diff_hunk, commit)
        parent = classified[0]
        reply_item = classified[1]

        self.assertTrue(require_correlation(parent).likely_miss)
        self.assertIn("review_comment:2", parent.evidence_ids)
        reply_correlation = require_correlation(reply_item)
        self.assertFalse(reply_correlation.likely_miss)
        self.assertEqual(reply_item.evidence_ids, [])
        self.assertEqual(reply_item.primary_class, "not_actionable")
        self.assertNotIn("likely_miss_correlation", reply_item.secondary_tags)

    def test_fixed_reply_with_new_finding_remains_classifiable(self):
        item = correlated(
            raw_signal(
                "review_comment",
                "review_comment:1",
                body="Fixed, but coverage is still missing for the empty input case.",
                created_at="2026-05-04T01:30:00Z",
                raw={"id": "2", "in_reply_to_id": "1"},
            )
        )[0]

        self.assertEqual(item.primary_class, "miss")
        self.assertTrue(item.manual_triage)

    def test_correlation_does_not_cross_prs_in_backfill_batches(self):
        feedback = raw_signal(
            "review_comment",
            "review_comment:1",
            body="Can you explain this branch?",
            created_at="2026-05-04T01:00:00Z",
            pr_number=101,
            path="automation/feedback-loop/feedback_loop/pipeline/classify.py",
            line=12,
        )
        changed_file = raw_signal(
            "changed_file",
            "file:other-pr",
            pr_number=202,
            path="automation/feedback-loop/feedback_loop/pipeline/classify.py",
        )
        diff_hunk = raw_signal(
            "diff_hunk",
            "hunk:other-pr",
            pr_number=202,
            path="automation/feedback-loop/feedback_loop/pipeline/classify.py",
            raw={"new_start": 10, "new_count": 5},
        )
        commit = raw_signal(
            "commit",
            "commit:other-pr",
            body="fix branch explanation",
            created_at="2026-05-04T02:00:00Z",
            pr_number=202,
        )

        item = correlated(feedback, changed_file, diff_hunk, commit)[0]

        self.assertFalse(require_correlation(item).likely_miss)
        self.assertEqual(item.evidence_ids, [])

    def test_unrelated_later_commit_does_not_make_inline_question_a_miss(self):
        feedback = raw_signal(
            "review_comment",
            "review_comment:1",
            body="Can you explain this branch?",
            created_at="2026-05-04T01:00:00Z",
            path="automation/feedback-loop/feedback_loop/pipeline/classify.py",
            line=12,
        )
        changed_file = raw_signal(
            "changed_file",
            "file:classify",
            path="automation/feedback-loop/feedback_loop/pipeline/classify.py",
        )
        diff_hunk = raw_signal(
            "diff_hunk",
            "hunk:classify:10",
            path="automation/feedback-loop/feedback_loop/pipeline/classify.py",
            raw={"new_start": 10, "new_count": 5},
        )
        commit = raw_signal(
            "commit",
            "commit:wip",
            body="wip cleanup",
            created_at="2026-05-04T02:00:00Z",
        )

        item = correlated(feedback, changed_file, diff_hunk, commit)[0]

        correlation = require_correlation(item)
        self.assertFalse(correlation.likely_miss)
        self.assertNotIn("commit:wip", item.evidence_ids)
        self.assertEqual(item.primary_class, "question")
        self.assertTrue(item.is_excluded)
        self.assertNotIn("likely_miss_correlation", item.secondary_tags)

    def test_correlated_rename_question_without_quality_language_is_not_a_miss(self):
        feedback = raw_signal(
            "review_comment",
            "review_comment:1",
            body="Could we rename this variable?",
            created_at="2026-05-04T01:00:00Z",
            path="automation/feedback-loop/feedback_loop/pipeline/classify.py",
            line=12,
        )
        changed_file = raw_signal(
            "changed_file",
            "file:classify",
            path="automation/feedback-loop/feedback_loop/pipeline/classify.py",
        )
        diff_hunk = raw_signal(
            "diff_hunk",
            "hunk:classify:10",
            path="automation/feedback-loop/feedback_loop/pipeline/classify.py",
            raw={"new_start": 10, "new_count": 5},
        )
        commit = raw_signal(
            "commit",
            "commit:rename",
            body="fix variable lint",
            created_at="2026-05-04T02:00:00Z",
        )

        item = correlated(feedback, changed_file, diff_hunk, commit)[0]

        correlation = require_correlation(item)
        self.assertTrue(correlation.likely_miss)
        self.assertIn("commit:rename", item.evidence_ids)
        self.assertEqual(item.primary_class, "preference")
        self.assertTrue(item.is_excluded)
        self.assertIsNone(item.suggested_destination)

    def test_correlated_nit_without_quality_language_remains_excluded(self):
        feedback = raw_signal(
            "review_comment",
            "review_comment:1",
            body="Nit: rename this variable.",
            created_at="2026-05-04T01:00:00Z",
            path="automation/feedback-loop/feedback_loop/pipeline/classify.py",
            line=12,
        )
        changed_file = raw_signal(
            "changed_file",
            "file:classify",
            path="automation/feedback-loop/feedback_loop/pipeline/classify.py",
        )
        diff_hunk = raw_signal(
            "diff_hunk",
            "hunk:classify:10",
            path="automation/feedback-loop/feedback_loop/pipeline/classify.py",
            raw={"new_start": 10, "new_count": 5},
        )
        commit = raw_signal(
            "commit",
            "commit:rename",
            body="fix variable lint",
            created_at="2026-05-04T02:00:00Z",
        )

        item = correlated(feedback, changed_file, diff_hunk, commit)[0]

        self.assertTrue(require_correlation(item).likely_miss)
        self.assertEqual(item.primary_class, "nit")
        self.assertTrue(item.is_excluded)
        self.assertIsNone(item.suggested_destination)

    def test_correlated_question_without_quality_language_remains_excluded(self):
        feedback = raw_signal(
            "review_comment",
            "review_comment:1",
            body="Can you explain this branch?",
            created_at="2026-05-04T01:00:00Z",
            path="automation/feedback-loop/feedback_loop/pipeline/classify.py",
            line=12,
        )
        changed_file = raw_signal(
            "changed_file",
            "file:classify",
            path="automation/feedback-loop/feedback_loop/pipeline/classify.py",
        )
        diff_hunk = raw_signal(
            "diff_hunk",
            "hunk:classify:10",
            path="automation/feedback-loop/feedback_loop/pipeline/classify.py",
            raw={"new_start": 10, "new_count": 5},
        )
        commit = raw_signal(
            "commit",
            "commit:branch",
            body="fix branch cleanup",
            created_at="2026-05-04T02:00:00Z",
        )

        item = correlated(feedback, changed_file, diff_hunk, commit)[0]

        self.assertTrue(require_correlation(item).likely_miss)
        self.assertEqual(item.primary_class, "question")
        self.assertTrue(item.is_excluded)
        self.assertIsNone(item.suggested_destination)

    def test_correlated_bot_finding_still_promotes(self):
        feedback = raw_signal(
            "bot_review",
            "bot_review:1",
            body="Potential issue.",
            created_at="2026-05-04T01:00:00Z",
            raw={"resolved": True, "reviewed_head_sha": "oldsha"},
        )
        metadata = raw_signal(
            "pr_metadata",
            "pr:squareup/wallet#123",
            raw={"shas": {"head": "newsha"}},
        )

        item = correlated(feedback, metadata)[0]

        self.assertTrue(require_correlation(item).likely_miss)
        self.assertEqual(item.primary_class, "miss")
        self.assertFalse(item.is_excluded)

    def test_correlated_plain_language_defect_promotes_without_exclusion_pattern(self):
        feedback = raw_signal(
            "review_comment",
            "review_comment:1",
            body="This branch is wrong.",
            created_at="2026-05-04T01:00:00Z",
            path="automation/feedback-loop/feedback_loop/pipeline/classify.py",
            line=12,
        )
        changed_file = raw_signal(
            "changed_file",
            "file:classify",
            path="automation/feedback-loop/feedback_loop/pipeline/classify.py",
        )
        diff_hunk = raw_signal(
            "diff_hunk",
            "hunk:classify:10",
            path="automation/feedback-loop/feedback_loop/pipeline/classify.py",
            raw={"new_start": 10, "new_count": 5},
        )
        commit = raw_signal(
            "commit",
            "commit:branch",
            body="fix branch handling",
            created_at="2026-05-04T02:00:00Z",
        )

        item = correlated(feedback, changed_file, diff_hunk, commit)[0]

        self.assertTrue(require_correlation(item).likely_miss)
        self.assertEqual(item.primary_class, "miss")
        self.assertFalse(item.is_excluded)
        self.assertEqual(item.suggested_destination, "agents_check")

    def test_unrelated_later_reply_does_not_correlate_across_threads(self):
        feedback = raw_signal(
            "issue_comment",
            "issue_comment:1",
            body="Can you explain this branch?",
            created_at="2026-05-04T01:00:00Z",
            raw={"thread_id": "discussion:1"},
        )
        reply = raw_signal(
            "issue_comment",
            "issue_comment:2",
            body="Updated the PR description.",
            created_at="2026-05-04T01:30:00Z",
            raw={"thread_id": "discussion:2"},
        )

        item = correlated(feedback, reply)[0]

        correlation = require_correlation(item)
        self.assertFalse(correlation.likely_miss)
        self.assertNotIn("issue_comment:2", item.evidence_ids)
        self.assertEqual(item.primary_class, "question")
        self.assertTrue(item.is_excluded)

    def test_correlates_bot_review_to_thread_resolution_and_new_head(self):
        feedback = raw_signal(
            "bot_review",
            "bot_review:1",
            body="Missing bound.",
            created_at="2026-05-04T01:00:00Z",
            raw={"resolved": True, "reviewed_head_sha": "oldsha"},
        )
        metadata = raw_signal(
            "pr_metadata",
            "pr:squareup/wallet#123",
            raw={"shas": {"head": "newsha"}},
        )

        item = correlated(feedback, metadata)[0]

        correlation = require_correlation(item)
        self.assertTrue(correlation.likely_miss)
        self.assertIn("pr:squareup/wallet#123", item.evidence_ids)
        self.assertIn("review thread metadata indicates resolution", item.rationale)
        self.assertIn("feedback reviewed an earlier head than the final PR head", item.rationale)

    def test_keeps_unmatched_feedback_reviewable(self):
        item = correlated(
            raw_signal(
                "review",
                "review:1",
                body="General concern.",
                created_at="2026-05-04T01:00:00Z",
            )
        )[0]

        correlation = require_correlation(item)
        self.assertFalse(correlation.likely_miss)
        self.assertEqual(item.primary_class, "not_actionable")
        self.assertEqual(item.confidence, 0.3)
        self.assertEqual(item.evidence_ids, [])
        self.assertEqual(
            item.rationale,
            "no deterministic follow-up evidence found; no actionable classifier evidence found",
        )
        self.assertNotIn("likely_miss_correlation", item.secondary_tags)

    def test_non_feedback_signals_are_unchanged(self):
        signal = correlated(raw_signal("commit", "commit:abc", body="fix tests"))[0]

        self.assertIsNone(signal.correlation)
        self.assertEqual(signal.body, "fix tests")

    def test_low_confidence_miss_like_feedback_routes_to_manual_triage(self):
        item = correlated(
            raw_signal(
                "review_comment",
                "review_comment:1",
                body="Missing validation for this edge case.",
                path="app/validation/File.kt",
            )
        )[0]

        self.assertEqual(item.primary_class, "miss")
        self.assertEqual(item.confidence, 0.45)
        self.assertTrue(item.manual_triage)
        self.assertIsNone(item.suggested_destination)
        self.assertIn("manual_triage", item.secondary_tags)
        self.assertEqual(item.area, "app")
        self.assertIn("human_review", item.secondary_tags)

    def test_classifies_validation_check_failures(self):
        item = correlated(
            raw_signal(
                "check",
                "check:validation",
                body="ai-context-check | failure",
                raw={"source": "workflow_run", "name": "ai-context-check"},
            )
        )[0]

        self.assertEqual(item.primary_class, "validation_failure")
        self.assertEqual(item.severity, "medium")
        self.assertEqual(item.confidence, 0.8)
        self.assertEqual(item.suggested_destination, "test_or_linter")
        self.assertFalse(item.manual_triage)
        self.assertIn("validation", item.secondary_tags)
        self.assertIn("failed validation signal", item.rationale)

    def test_classifies_ci_check_failures_with_ci_rationale(self):
        item = correlated(
            raw_signal(
                "check",
                "check:unit-tests",
                body="Unit Tests | failure",
                raw={"source": "workflow_run", "name": "Unit Tests"},
            )
        )[0]

        self.assertEqual(item.primary_class, "ci_failure")
        self.assertEqual(item.suggested_destination, "test_or_linter")
        self.assertIn("failed CI signal", item.rationale)
        self.assertNotIn("failed validation signal", item.rationale)

    def test_failed_check_metadata_routes_correlated_miss_to_mechanical_destination(self):
        feedback = raw_signal(
            "review_comment",
            "review_comment:1",
            body="This is broken for empty input.",
            created_at="2026-05-04T01:00:00Z",
            path="automation/feedback-loop/feedback_loop/pipeline/classify.py",
            line=12,
        )
        changed_file = raw_signal(
            "changed_file",
            "file:classify",
            path="automation/feedback-loop/feedback_loop/pipeline/classify.py",
        )
        diff_hunk = raw_signal(
            "diff_hunk",
            "hunk:classify:10",
            path="automation/feedback-loop/feedback_loop/pipeline/classify.py",
            raw={"new_start": 10, "new_count": 5},
        )
        commit = raw_signal(
            "commit",
            "commit:empty-input",
            body="fix empty input",
            created_at="2026-05-04T02:00:00Z",
        )
        check = raw_signal(
            "check",
            "check:opaque",
            body="buildkite/wallet/pr | failure",
            created_at="2026-05-04T02:15:00Z",
            raw={"name": "Unit Tests"},
        )

        item = correlated(feedback, changed_file, diff_hunk, commit, check)[0]

        self.assertTrue(require_correlation(item).likely_miss)
        self.assertEqual(item.primary_class, "miss")
        self.assertEqual(item.suggested_destination, "test_or_linter")
        self.assertIn("Unit Tests", item.rationale)

    def test_classifies_false_positive_feedback_without_destination(self):
        item = correlated(
            raw_signal(
                "bot_review",
                "bot_review:1",
                body="False positive, this is safe as-is.",
                raw={"provider": "codex_security_review"},
            )
        )[0]

        self.assertEqual(item.primary_class, "false_positive")
        self.assertEqual(item.severity, "low")
        self.assertEqual(item.confidence, 0.75)
        self.assertIsNone(item.suggested_destination)
        self.assertIn("codex_review", item.secondary_tags)


if __name__ == "__main__":
    unittest.main()
