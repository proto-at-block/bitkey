"""Smoke tests for the feedback-loop scaffold.

These assert the substrate-agnostic contracts hold:
  - the CLI parses and orchestrates without import errors;
  - remaining stubbed stages fail loudly with a ticket pointer (not silently);

Run from repo root: python -m pytest automation/feedback-loop/tests
Or from automation/feedback-loop/: python -m unittest discover tests
"""

from __future__ import annotations

import io
import os
import sys
import unittest
from types import SimpleNamespace
from unittest.mock import patch

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from feedback_loop.cli import main  # noqa: E402
from feedback_loop.config import RunConfig  # noqa: E402
from feedback_loop.models import Cluster, Proposal, ProposalEvalArtifact, RawSignal  # noqa: E402
from feedback_loop.pipeline import emit  # noqa: E402


def raw_signal(
    kind: str,
    source_id: str,
    *,
    pr_number: int,
    body: str = "",
    created_at: str = "",
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


class TestCli(unittest.TestCase):
    def test_run_config_idempotency_key_is_stable(self):
        cfg = RunConfig(repo="SquareUp/Wallet", harvest_version="2")

        self.assertEqual(cfg.idempotency_key(123), "squareup/wallet/pr/123/harvest-v2")

    def test_run_config_idempotency_key_rejects_invalid_inputs(self):
        with self.assertRaisesRegex(ValueError, "pr_number"):
            RunConfig().idempotency_key(0)
        with self.assertRaisesRegex(ValueError, "repo"):
            RunConfig(repo="wallet").idempotency_key(123)
        with self.assertRaisesRegex(ValueError, "harvest_version"):
            RunConfig(harvest_version=" ").idempotency_key(123)

    def test_help_runs(self):
        with self.assertRaises(SystemExit) as ctx:
            main(["--help"])
        self.assertEqual(ctx.exception.code, 0)

    def test_successful_harvest_reaches_emit_dry_run_and_exits_0(self):
        with patch("feedback_loop.pipeline.harvest.harvest_pr", return_value=[]):
            rc = main(["run", "--pr", "https://github.com/squareup/wallet/pull/1", "--dry-run"])
        self.assertEqual(rc, 0)

    def test_execute_noops_when_no_proposals_are_ready(self):
        with (
            patch("feedback_loop.pipeline.harvest.harvest_pr", return_value=[]),
            patch("feedback_loop.pipeline.normalize.normalize", return_value=[]),
            patch("feedback_loop.pipeline.classify.classify", return_value=[]),
            patch("feedback_loop.pipeline.cluster.cluster", return_value=[]),
            patch(
                "feedback_loop.pipeline.triage.build_triage_report",
                return_value=SimpleNamespace(markdown=""),
            ),
            patch("feedback_loop.pipeline.propose.propose", return_value=[]),
            patch("feedback_loop.pipeline.emit.emit", return_value=[]) as emit_mock,
        ):
            rc = main(["run", "--pr", "https://github.com/squareup/wallet/pull/1", "--execute"])

        self.assertEqual(rc, 0)
        emit_mock.assert_not_called()

    def test_non_ready_proposals_are_reported_and_skipped_in_dry_run(self):
        not_ready = Proposal(
            cluster=Cluster(theme="miss:docs", signals=[]),
            destination="docs",
            summary="Docs follow-up that did not pass eval.",
        )
        stderr = io.StringIO()
        with (
            patch("feedback_loop.pipeline.harvest.harvest_pr", return_value=[]),
            patch("feedback_loop.pipeline.normalize.normalize", return_value=[]),
            patch("feedback_loop.pipeline.classify.classify", return_value=[]),
            patch("feedback_loop.pipeline.cluster.cluster", return_value=[]),
            patch(
                "feedback_loop.pipeline.triage.build_triage_report",
                return_value=SimpleNamespace(markdown=""),
            ),
            patch("feedback_loop.pipeline.propose.propose", return_value=[not_ready]),
            patch("feedback_loop.pipeline.emit.emit", return_value=[]) as emit_mock,
            patch("sys.stderr", new=stderr),
        ):
            rc = main(["run", "--pr", "https://github.com/squareup/wallet/pull/1", "--dry-run"])

        self.assertEqual(rc, 0)
        emit_mock.assert_not_called()
        self.assertIn("skipped 1 generated proposal pending eval/PR-ready", stderr.getvalue())

    def test_non_ready_proposals_block_execute_before_emit(self):
        not_ready = Proposal(
            cluster=Cluster(theme="miss:docs", signals=[]),
            destination="docs",
            summary="Docs follow-up that did not pass eval.",
        )
        stderr = io.StringIO()
        with (
            patch("feedback_loop.pipeline.harvest.harvest_pr", return_value=[]),
            patch("feedback_loop.pipeline.normalize.normalize", return_value=[]),
            patch("feedback_loop.pipeline.classify.classify", return_value=[]),
            patch("feedback_loop.pipeline.cluster.cluster", return_value=[]),
            patch(
                "feedback_loop.pipeline.triage.build_triage_report",
                return_value=SimpleNamespace(markdown=""),
            ),
            patch("feedback_loop.pipeline.propose.propose", return_value=[not_ready]),
            patch("feedback_loop.pipeline.emit.emit", return_value=[]) as emit_mock,
            patch("sys.stderr", new=stderr),
        ):
            rc = main(["run", "--pr", "https://github.com/squareup/wallet/pull/1", "--execute"])

        self.assertEqual(rc, 3)
        emit_mock.assert_not_called()
        self.assertIn("proposal eval blocked", stderr.getvalue())

    def test_successful_emit_results_exit_zero(self):
        ready = Proposal(
            cluster=Cluster(theme="miss:docs", signals=[]),
            destination="docs",
            summary="Docs follow-up that passed eval.",
            eval_passed=True,
            eval_state="pr_ready",
            eval_artifact=ProposalEvalArtifact(
                state="pr_ready",
                cluster_theme="miss:docs",
                rubric_markdown="Status: PASS",
            ),
        )
        with (
            patch("feedback_loop.pipeline.harvest.harvest_pr", return_value=[]),
            patch("feedback_loop.pipeline.normalize.normalize", return_value=[]),
            patch("feedback_loop.pipeline.classify.classify", return_value=[]),
            patch("feedback_loop.pipeline.cluster.cluster", return_value=[]),
            patch(
                "feedback_loop.pipeline.triage.build_triage_report",
                return_value=SimpleNamespace(markdown=""),
            ),
            patch("feedback_loop.pipeline.propose.propose", return_value=[ready]),
            patch("feedback_loop.pipeline.emit.emit", return_value=[object()]) as emit_mock,
        ):
            rc = main(["run", "--pr", "https://github.com/squareup/wallet/pull/1", "--dry-run"])

        self.assertEqual(rc, 0)
        emit_mock.assert_called_once()
        self.assertEqual(emit_mock.call_args.args[1], [ready])

    def test_execute_ready_proposals_fail_before_unwired_linear_writer(self):
        ready = Proposal(
            cluster=Cluster(theme="miss:docs", signals=[]),
            destination="docs",
            summary="Docs follow-up that passed eval.",
            eval_passed=True,
            eval_state="pr_ready",
            eval_artifact=ProposalEvalArtifact(
                state="pr_ready",
                cluster_theme="miss:docs",
                rubric_markdown="Status: PASS",
            ),
        )
        stderr = io.StringIO()
        with (
            patch("feedback_loop.pipeline.harvest.harvest_pr", return_value=[]),
            patch("feedback_loop.pipeline.normalize.normalize", return_value=[]),
            patch("feedback_loop.pipeline.classify.classify", return_value=[]),
            patch("feedback_loop.pipeline.cluster.cluster", return_value=[]),
            patch(
                "feedback_loop.pipeline.triage.build_triage_report",
                return_value=SimpleNamespace(markdown=""),
            ),
            patch("feedback_loop.pipeline.propose.propose", return_value=[ready]),
            patch("feedback_loop.pipeline.emit.emit", return_value=[]) as emit_mock,
            patch("sys.stderr", new=stderr),
        ):
            rc = main(["run", "--pr", "https://github.com/squareup/wallet/pull/1", "--execute"])

        self.assertEqual(rc, 3)
        emit_mock.assert_not_called()
        self.assertIn("Linear writer is not wired", stderr.getvalue())

    def test_backfill_clusters_the_bounded_window_once(self):
        captured_clusters = []

        def fake_propose(_cfg, clusters):
            captured_clusters.extend(clusters)
            raise NotImplementedError("stop after clustering")

        first_pr = [
            raw_signal(
                "review_comment",
                "review_comment:1",
                pr_number=101,
                body="Please add a regression test here.",
                created_at="2026-05-04T01:00:00Z",
                path="automation/feedback-loop/feedback_loop/pipeline/harvest.py",
                line=12,
            ),
            raw_signal(
                "diff_hunk",
                "hunk:101",
                pr_number=101,
                path="automation/feedback-loop/feedback_loop/pipeline/harvest.py",
                raw={"new_start": 10, "new_count": 5},
            ),
            raw_signal(
                "commit",
                "commit:101",
                pr_number=101,
                body="fix review comment with regression test",
                created_at="2026-05-04T02:00:00Z",
            ),
        ]
        second_pr = [
            raw_signal(
                "review_comment",
                "review_comment:2",
                pr_number=202,
                body="Please add a regression test for this branch.",
                created_at="2026-05-05T01:00:00Z",
                path="automation/feedback-loop/feedback_loop/pipeline/normalize.py",
                line=20,
            ),
            raw_signal(
                "diff_hunk",
                "hunk:202",
                pr_number=202,
                path="automation/feedback-loop/feedback_loop/pipeline/normalize.py",
                raw={"new_start": 18, "new_count": 5},
            ),
            raw_signal(
                "commit",
                "commit:202",
                pr_number=202,
                body="fix review comment with regression test",
                created_at="2026-05-05T02:00:00Z",
            ),
        ]

        stdout = io.StringIO()
        stderr = io.StringIO()
        with patch(
            "feedback_loop.pipeline.harvest.list_merged_prs",
            return_value=[
                "https://github.com/squareup/wallet/pull/101",
                "https://github.com/squareup/wallet/pull/202",
            ],
        ), patch(
            "feedback_loop.pipeline.harvest.harvest_pr",
            side_effect=[first_pr, second_pr],
        ), patch(
            "feedback_loop.pipeline.propose.propose",
            side_effect=fake_propose,
        ), patch("sys.stdout", new=stdout), patch("sys.stderr", new=stderr):
            rc = main(["run", "--backfill", "--since", "2026-05-01", "--limit", "2"])

        self.assertEqual(rc, 3)
        self.assertEqual(len(captured_clusters), 1)
        self.assertEqual(captured_clusters[0].frequency, 2)
        self.assertIn("Frequency: `2` distinct PR(s)", stdout.getvalue())

    def test_backfill_classifies_each_pr_before_merging_clusters(self):
        captured_clusters = []

        def fake_propose(_cfg, clusters):
            captured_clusters.extend(clusters)
            raise NotImplementedError("stop after clustering")

        first_pr = [
            raw_signal(
                "review_comment",
                "review_comment:1",
                pr_number=101,
                body="Can you explain this branch?",
                created_at="2026-05-04T01:00:00Z",
                path="automation/feedback-loop/feedback_loop/pipeline/classify.py",
                line=12,
            )
        ]
        second_pr = [
            raw_signal(
                "diff_hunk",
                "hunk:cross-pr",
                pr_number=101,
                path="automation/feedback-loop/feedback_loop/pipeline/classify.py",
                raw={"new_start": 10, "new_count": 5},
            ),
            raw_signal(
                "commit",
                "commit:cross-pr",
                pr_number=101,
                body="fix branch cleanup",
                created_at="2026-05-04T02:00:00Z",
            ),
        ]

        stdout = io.StringIO()
        stderr = io.StringIO()
        with patch(
            "feedback_loop.pipeline.harvest.list_merged_prs",
            return_value=[
                "https://github.com/squareup/wallet/pull/101",
                "https://github.com/squareup/wallet/pull/202",
            ],
        ), patch(
            "feedback_loop.pipeline.harvest.harvest_pr",
            side_effect=[first_pr, second_pr],
        ), patch(
            "feedback_loop.pipeline.propose.propose",
            side_effect=fake_propose,
        ), patch("sys.stdout", new=stdout), patch("sys.stderr", new=stderr):
            rc = main(["run", "--backfill", "--since", "2026-05-01", "--limit", "2"])

        self.assertEqual(rc, 3)
        signals = [signal for cluster in captured_clusters for signal in cluster.signals]
        feedback = next(signal for signal in signals if signal.source_id == "review_comment:1")
        self.assertEqual(feedback.primary_class, "question")
        self.assertTrue(feedback.is_excluded)
        self.assertIsNotNone(feedback.correlation)
        self.assertFalse(feedback.correlation.likely_miss)
        self.assertEqual(feedback.evidence_ids, [])

    def test_wrong_repo_pr_fails_before_harvest(self):
        rc = main(["run", "--pr", "https://github.com/squareup/not-wallet/pull/1"])

        self.assertEqual(rc, 2)


class TestEmitDryRun(unittest.TestCase):
    def test_emit_dry_run_allows_empty_plan(self):
        self.assertEqual(emit.emit(RunConfig(dry_run=True), []), [])


if __name__ == "__main__":
    unittest.main()
