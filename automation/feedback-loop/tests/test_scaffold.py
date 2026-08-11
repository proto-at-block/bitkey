"""Smoke tests for the feedback-loop scaffold.

These assert the substrate-agnostic contracts hold:
  - the CLI parses and orchestrates without import errors;
  - remaining stubbed stages fail loudly with a ticket pointer (not silently);

Run from repo root: python -m pytest automation/feedback-loop/tests
Or from automation/feedback-loop/: python -m unittest discover tests
"""

from __future__ import annotations

import io
import json
import os
from pathlib import Path
import sys
import tempfile
import unittest
from types import SimpleNamespace
from unittest.mock import patch

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from feedback_loop.cli import main  # noqa: E402
from feedback_loop.cluster_memory import ClusterMemoryReadResult, LinearMemoryUnavailable  # noqa: E402
from feedback_loop.config import RunConfig  # noqa: E402
from feedback_loop.linear_control import BUILDERBOT_APPROVAL_LABEL  # noqa: E402
from feedback_loop.llm import FakeLlmClient  # noqa: E402
from feedback_loop.models import (  # noqa: E402
    Cluster,
    NormalizedSignal,
    RawSignal,
)
from feedback_loop.pipeline import emit  # noqa: E402
from feedback_loop.pipeline.llm_classify import ClassifyStageResult  # noqa: E402
from feedback_loop.pipeline.llm_cluster import LlmClusterStageResult  # noqa: E402


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


def normalized_signal(source_id: str, *, pr_number: int = 1) -> NormalizedSignal:
    raw = raw_signal(
        "review_comment",
        source_id,
        pr_number=pr_number,
        body="Please add a regression test.",
        path="automation/feedback-loop/feedback_loop/pipeline/cluster.py",
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
        body=raw.body,
        primary_class="miss",
        severity="medium",
        confidence=0.9,
        suggested_destination="test_or_linter",
    )


def promote_cluster(
    slug: str,
    *signals_in_cluster: NormalizedSignal,
    severity: str = "high",
    frequency: int = 2,
    rank: float = 8.0,
) -> Cluster:
    members = list(signals_in_cluster)
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
        suggested_destination="docs",
        decision="promote",
        summary="LLM route docs follow-up.",
        source_urls=[item.source_url for item in members],
    )


class FakeLinearClient:
    def __init__(self):
        self.written = []

    def read_cluster_memory(self):
        return ClusterMemoryReadResult(status="ok")

    def upsert_cluster_memory(self, plan, existing=None):
        self.written.append((plan, existing))
        return f"https://linear.app/squareup/issue/BKW-{len(self.written)}/memory"


class UnavailableLinearClient:
    def read_cluster_memory(self):
        raise LinearMemoryUnavailable("test Linear outage")


class TestCli(unittest.TestCase):
    def test_help_runs(self):
        with self.assertRaises(SystemExit) as ctx:
            main(["--help"])
        self.assertEqual(ctx.exception.code, 0)

    def test_successful_harvest_reaches_emit_dry_run_and_exits_0(self):
        with patch("feedback_loop.pipeline.harvest.harvest_pr", return_value=[]):
            rc = main(["run", "--pr", "https://github.com/squareup/wallet/pull/1", "--dry-run"])
        self.assertEqual(rc, 0)

    def test_pr_dry_run_output_dir_writes_empty_bundle(self):
        with tempfile.TemporaryDirectory() as tmp:
            output_dir = Path(tmp)
            with patch("feedback_loop.pipeline.harvest.harvest_pr", return_value=[]):
                rc = main(
                    [
                        "run",
                        "--pr",
                        "https://github.com/squareup/wallet/pull/1",
                        "--dry-run",
                        "--output-dir",
                        str(output_dir),
                    ]
                )

            self.assertEqual(rc, 0)
            self.assertTrue((output_dir / "run-summary.json").exists())
            self.assertTrue((output_dir / "triage-report.md").exists())
            self.assertTrue((output_dir / "triage-report-full.md").exists())
            self.assertTrue((output_dir / "triage-summary.json").exists())
            self.assertTrue((output_dir / "proposals.json").exists())
            self.assertTrue((output_dir / "proposal-evals.json").exists())
            self.assertTrue((output_dir / "llm-learnings.json").exists())
            self.assertTrue((output_dir / "llm-debug.json").exists())
            self.assertTrue((output_dir / "eval-blocked.json").exists())
            self.assertTrue((output_dir / "cluster-memory.json").exists())
            self.assertTrue((output_dir / "emit-preview").is_dir())

            summary = json.loads((output_dir / "run-summary.json").read_text())
            self.assertEqual(summary["mode"], "pr")
            self.assertEqual(summary["repo"], "squareup/wallet")
            self.assertEqual(
                summary["pr_urls"],
                ["https://github.com/squareup/wallet/pull/1"],
            )
            self.assertEqual(summary["proposal_readiness"]["total"], 0)
            self.assertEqual(summary["proposal_eval"]["eval_count"], 0)
            self.assertEqual(summary["proposal_eval"]["errors"], 0)
            self.assertEqual(summary["linear_memory"]["read_status"], "skipped")
            # No LLM client is configured in unit tests: dry-run degrades to a facts-only report.
            self.assertIn("Facts-Only Report", (output_dir / "triage-report.md").read_text())

    def test_dry_run_with_fake_linear_reader_writes_cluster_memory_artifact(self):
        cluster_item = Cluster(
            slug="automation-test-coverage",
            signals=[normalized_signal("review_comment:memory")],
            area="automation",
            severity="medium",
            frequency=1,
            rank=2.0,
            suggested_destination="test_or_linter",
            decision="gather_more_evidence",
            summary="1 PR(s), 1 signal(s), medium severity: missing automation test",
            source_urls=["https://github.com/squareup/wallet/pull/1#review_comment:memory"],
        )
        with tempfile.TemporaryDirectory() as tmp:
            output_dir = Path(tmp)
            with (
                patch("feedback_loop.cli._linear_memory_client_for_config", return_value=FakeLinearClient()),
                patch("feedback_loop.pipeline.harvest.harvest_pr", return_value=[]),
                patch("feedback_loop.pipeline.harvest.normalize_signals", return_value=[]),
                patch(
                    "feedback_loop.pipeline.llm_classify.classify_signals",
                    return_value=ClassifyStageResult(signals=[]),
                ),
                patch(
                    "feedback_loop.pipeline.llm_cluster.cluster_signals",
                    return_value=LlmClusterStageResult(clusters=[cluster_item]),
                ),
                patch(
                    "feedback_loop.pipeline.llm_evaluator._client_from_config",
                    return_value=FakeLlmClient([{"learnings": []}]),
                ),
            ):
                rc = main(
                    [
                        "run",
                        "--pr",
                        "https://github.com/squareup/wallet/pull/1",
                        "--dry-run",
                        "--output-dir",
                        str(output_dir),
                    ]
                )

            self.assertEqual(rc, 0)
            memory = json.loads((output_dir / "cluster-memory.json").read_text())
            summary = json.loads((output_dir / "run-summary.json").read_text())
            self.assertEqual(memory["read"]["status"], "ok")
            self.assertEqual(memory["planned_upserts"][0]["action"], "create")
            self.assertEqual(memory["planned_upserts"][0]["status"], "needs_triage")
            self.assertEqual(summary["linear_memory"]["memory_upserts_planned"], 1)

    def test_dry_run_with_linear_unavailable_records_warning(self):
        with tempfile.TemporaryDirectory() as tmp:
            output_dir = Path(tmp)
            with (
                patch("feedback_loop.cli._linear_memory_client_for_config", return_value=UnavailableLinearClient()),
                patch("feedback_loop.pipeline.harvest.harvest_pr", return_value=[]),
            ):
                rc = main(
                    [
                        "run",
                        "--pr",
                        "https://github.com/squareup/wallet/pull/1",
                        "--dry-run",
                        "--output-dir",
                        str(output_dir),
                    ]
                )

            self.assertEqual(rc, 0)
            summary = json.loads((output_dir / "run-summary.json").read_text())
            self.assertEqual(summary["linear_memory"]["read_status"], "unavailable")

    def test_end_to_end_dry_run_classifies_clusters_and_summarizes(self):
        signals = [
            raw_signal(
                "review_comment",
                "review_comment:coverage",
                pr_number=1,
                body="Missing validation coverage for empty input.",
                created_at="2026-05-04T01:00:00Z",
                path="app/Feature.kt",
                line=12,
            ),
            raw_signal(
                "pr_metadata",
                "pr:1",
                pr_number=1,
                raw={
                    "shas": {"base": "a" * 40, "head": "b" * 40},
                    "timestamps": {"merged_at": "2026-05-04T03:00:00Z"},
                },
            ),
            raw_signal(
                "changed_file",
                "file:feature-test",
                pr_number=1,
                path="app/src/commonTest/kotlin/FeatureTest.kt",
            ),
            raw_signal(
                "commit",
                "commit:coverage",
                pr_number=1,
                body="fix validation coverage for empty input",
                created_at="2026-05-04T02:00:00Z",
            ),
        ]
        client = FakeLlmClient(
            [
                {
                    "classifications": [
                        {
                            "signal_id": "review_comment:coverage",
                            "primary_class": "miss",
                            "severity": "medium",
                            "confidence": 0.9,
                            "exclusion_reason": None,
                            "suggested_destination": "test_or_linter",
                            "resolution": {
                                "state": "resolved_with_durable_coverage",
                                "evidence_signal_ids": ["commit:coverage"],
                                "coverage_paths": ["app/src/commonTest/kotlin/FeatureTest.kt"],
                                "rationale": "A later commit added the regression test.",
                            },
                            "rationale": "Trusted reviewer required validation coverage.",
                        }
                    ]
                },
                {
                    "clusters": [
                        {
                            "slug": "validation-coverage-empty-input",
                            "matched_memory_slug": None,
                            "title": "Validation coverage for empty input",
                            "summary": "Reviewers require empty-input validation coverage.",
                            "destination": "test_or_linter",
                            "area": "app",
                            "member_signal_ids": ["review_comment:coverage"],
                            "rationale": "Single recurring validation theme.",
                        }
                    ]
                },
            ]
        )

        with tempfile.TemporaryDirectory() as tmp:
            output_dir = Path(tmp)
            with (
                patch("feedback_loop.pipeline.harvest.harvest_pr", return_value=signals),
                patch(
                    "feedback_loop.pipeline.llm_evaluator._client_from_config",
                    return_value=client,
                ),
            ):
                rc = main(
                    [
                        "run",
                        "--pr",
                        "https://github.com/squareup/wallet/pull/1",
                        "--dry-run",
                        "--output-dir",
                        str(output_dir),
                    ]
                )

            self.assertEqual(rc, 0)
            self.assertEqual(
                [request["task"] for request in client.requests],
                ["classify_feedback_signals", "cluster_feedback_signals"],
            )
            summary = json.loads((output_dir / "run-summary.json").read_text())
            counts = summary["counts_by_stage"]
            self.assertEqual(counts["classified_signals"], 1)
            self.assertEqual(counts["clusters"], 1)
            self.assertEqual(counts["already_covered_clusters"], 1)
            self.assertEqual(counts["generated_proposals"], 0)
            self.assertEqual(
                summary["comment_volume_summary"]["already_covered"],
                1,
            )

            triage_summary = json.loads((output_dir / "triage-summary.json").read_text())
            self.assertEqual(triage_summary[0]["slug"], "validation-coverage-empty-input")
            self.assertEqual(triage_summary[0]["decision"], "already_covered")
            self.assertEqual(
                triage_summary[0]["coverage_paths"],
                ["app/src/commonTest/kotlin/FeatureTest.kt"],
            )

            classifications = json.loads((output_dir / "classifications.json").read_text())
            clusters_artifact = json.loads((output_dir / "clusters.json").read_text())
            self.assertEqual(classifications[0]["primary_class"], "miss")
            self.assertEqual(
                classifications[0]["resolution"]["state"],
                "resolved_with_durable_coverage",
            )
            self.assertEqual(clusters_artifact[0]["slug"], "validation-coverage-empty-input")
            self.assertEqual(clusters_artifact[0]["decision"], "already_covered")

    def test_backfill_dry_run_output_dir_writes_processed_prs(self):
        with tempfile.TemporaryDirectory() as tmp:
            output_dir = Path(tmp)
            with (
                patch(
                    "feedback_loop.pipeline.harvest.list_merged_prs",
                    return_value=[
                        "https://github.com/squareup/wallet/pull/101",
                        "https://github.com/squareup/wallet/pull/202",
                    ],
                ),
                patch("feedback_loop.pipeline.harvest.harvest_pr", return_value=[]),
            ):
                rc = main(
                    [
                        "run",
                        "--backfill",
                        "--since",
                        "2026-05-01",
                        "--limit",
                        "2",
                        "--dry-run",
                        "--output-dir",
                        str(output_dir),
                    ]
                )

            self.assertEqual(rc, 0)
            summary = json.loads((output_dir / "run-summary.json").read_text())
            self.assertEqual(summary["mode"], "backfill")
            self.assertEqual(
                summary["pr_urls"],
                [
                    "https://github.com/squareup/wallet/pull/101",
                    "https://github.com/squareup/wallet/pull/202",
                ],
            )
            self.assertEqual(summary["counts_by_stage"]["harvested_signals"], 0)

    def test_execute_noops_when_no_proposals_are_ready(self):
        linear = FakeLinearClient()
        stdout = io.StringIO()
        with tempfile.TemporaryDirectory() as tmp:
            output_dir = Path(tmp)
            with (
                patch("feedback_loop.cli._linear_memory_client_for_config", return_value=linear),
                patch("feedback_loop.pipeline.harvest.harvest_pr", return_value=[]),
                patch("feedback_loop.pipeline.harvest.normalize_signals", return_value=[]),
                patch(
                    "feedback_loop.pipeline.llm_classify.classify_signals",
                    return_value=ClassifyStageResult(signals=[]),
                ),
                patch(
                    "feedback_loop.pipeline.llm_cluster.cluster_signals",
                    return_value=LlmClusterStageResult(clusters=[]),
                ),
                patch(
                    "feedback_loop.pipeline.llm_evaluator._client_from_config",
                    return_value=FakeLlmClient([{"learnings": []}]),
                ),
                patch("feedback_loop.pipeline.emit.emit", return_value=[]) as emit_mock,
                patch("sys.stdout", new=stdout),
            ):
                rc = main(
                    [
                        "run",
                        "--pr",
                        "https://github.com/squareup/wallet/pull/1",
                        "--execute",
                        "--output-dir",
                        str(output_dir),
                    ]
                )

            self.assertEqual(rc, 0)
            self.assertEqual(linear.written, [])
            emit_mock.assert_not_called()
            # Execute mode still records a run bundle even when nothing was written.
            summary = json.loads((output_dir / "run-summary.json").read_text())
            self.assertFalse(summary["dry_run"])
            self.assertEqual(summary["execution"]["issue_urls"], [])
            self.assertEqual(summary["execution"]["builderbot_triggered"], [])
            self.assertIn("execute complete", stdout.getvalue())

    def test_execute_writes_run_bundle_with_linear_urls(self):
        linear = FakeLinearClient()
        stdout = io.StringIO()
        signal_item = normalized_signal("review_comment:execute-bundle")
        cluster_item = promote_cluster("llm-route-docs", signal_item)
        client = approved_docs_client(cluster_item, signal_item, learning_id="learn-execute")
        with tempfile.TemporaryDirectory() as tmp:
            output_dir = Path(tmp)
            with (
                patch("feedback_loop.cli._linear_memory_client_for_config", return_value=linear),
                patch("feedback_loop.pipeline.harvest.harvest_pr", return_value=[]),
                patch("feedback_loop.pipeline.harvest.normalize_signals", return_value=[signal_item]),
                patch(
                    "feedback_loop.pipeline.llm_classify.classify_signals",
                    return_value=ClassifyStageResult(signals=[signal_item]),
                ),
                patch(
                    "feedback_loop.pipeline.llm_cluster.cluster_signals",
                    return_value=LlmClusterStageResult(clusters=[cluster_item]),
                ),
                patch(
                    "feedback_loop.pipeline.llm_evaluator._client_from_config",
                    return_value=client,
                ),
                patch("sys.stdout", new=stdout),
            ):
                rc = main(
                    [
                        "run",
                        "--pr",
                        "https://github.com/squareup/wallet/pull/1",
                        "--execute",
                        "--output-dir",
                        str(output_dir),
                    ]
                )

            self.assertEqual(rc, 0)
            self.assertTrue(linear.written)
            summary = json.loads((output_dir / "run-summary.json").read_text())
            self.assertFalse(summary["dry_run"])
            self.assertEqual(summary["execution"]["linear_write_status"], "written")
            self.assertTrue(summary["execution"]["issue_urls"])
            self.assertEqual(len(summary["execution"]["builderbot_triggered"]), 1)
            self.assertTrue(summary["execution"]["builderbot_triggered"][0]["issue_url"])
            self.assertTrue((output_dir / "linear-writes.json").exists())
            metadata_files = list((output_dir / "emit-preview").glob("*-metadata.json"))
            self.assertEqual(len(metadata_files), 1)
            metadata = json.loads(metadata_files[0].read_text())
            self.assertIn("linear.app", metadata["linear_issue_url"])
            self.assertIn("execute complete", stdout.getvalue())
            self.assertIn("builderbot trigger:", stdout.getvalue())

    def test_llm_can_write_emit_preview_for_approved_route(self):
        signal_item = normalized_signal("review_comment:llm-gate")
        cluster_item = promote_cluster("llm-route-docs", signal_item)
        client = approved_docs_client(cluster_item, signal_item, learning_id="learn-cli")
        with tempfile.TemporaryDirectory() as tmp:
            output_dir = Path(tmp)
            with (
                patch("feedback_loop.pipeline.harvest.harvest_pr", return_value=[]),
                patch("feedback_loop.pipeline.harvest.normalize_signals", return_value=[signal_item]),
                patch(
                    "feedback_loop.pipeline.llm_classify.classify_signals",
                    return_value=ClassifyStageResult(signals=[signal_item]),
                ),
                patch(
                    "feedback_loop.pipeline.llm_cluster.cluster_signals",
                    return_value=LlmClusterStageResult(clusters=[cluster_item]),
                ),
                patch(
                    "feedback_loop.pipeline.llm_evaluator._client_from_config",
                    return_value=client,
                ),
            ):
                rc = main(
                    [
                        "run",
                        "--pr",
                        "https://github.com/squareup/wallet/pull/1",
                        "--dry-run",
                        "--output-dir",
                        str(output_dir),
                    ]
                )

            self.assertEqual(rc, 0)
            summary = json.loads((output_dir / "run-summary.json").read_text())
            proposals = json.loads((output_dir / "proposals.json").read_text())
            self.assertEqual(summary["proposal_eval"]["pr_ready"], 1)
            self.assertEqual(summary["proposal_eval"]["planner_calls"], 1)
            self.assertEqual(summary["proposal_eval"]["repair_calls"], 0)
            self.assertEqual(summary["proposal_readiness"]["pr_ready"], 1)
            self.assertEqual(proposals[0]["learning_id"], "learn-cli")
            self.assertEqual(proposals[0]["eval_state"], "pr_ready")
            metadata_files = list((output_dir / "emit-preview").glob("*-metadata.json"))
            self.assertEqual(len(metadata_files), 1)
            metadata = json.loads(metadata_files[0].read_text())
            self.assertEqual(metadata["learning_id"], "learn-cli")

    def test_blocked_llm_proposal_writes_memory_without_builderbot_label(self):
        stderr = io.StringIO()
        linear = FakeLinearClient()
        signal_item = normalized_signal("review_comment:execute-failed-eval")
        cluster_item = promote_cluster(
            "durable-docs-coverage",
            signal_item,
            severity="medium",
            frequency=3,
            rank=6.0,
        )
        client = FakeLlmClient(
            [
                {
                    "learnings": [
                        {
                            "learning_id": "learn-blocked",
                            "cluster_slug": cluster_item.slug,
                            "evidence_urls": [signal_item.source_url],
                            "evidence_summary": "Linked review evidence needs durable docs.",
                            "agent_miss": "The agent missed the durable documentation update.",
                            "human_standard": "Future agents should update canonical docs when needed.",
                            "severity": "medium",
                            "confidence": 0.8,
                            "affected_area": "automation",
                            "routes": [
                                {
                                    "destination": "docs",
                                    "role": "primary",
                                    "summary": "Update feedback-loop docs for this durable learning.",
                                    "rationale": "The learning is human-facing guidance.",
                                    "target_artifacts": ["docs/docs/automation/feedback-loop.md"],
                                    "validation_commands": ["python -m unittest discover tests"],
                                    "file_changes": [
                                        {
                                            "path": "docs/docs/automation/feedback-loop.md",
                                            "content": "Feedback-loop docs update.\n",
                                        }
                                    ],
                                }
                            ],
                        }
                    ]
                },
                docs_planner_response(),
                {
                    "evaluations": [
                        {
                            "proposal_id": "llm:learn-blocked:docs",
                            "publishable": False,
                            "scores": {
                                "source_grounding": 5,
                                "actionability": 5,
                                "route_correctness": 5,
                                "noise_risk": 5,
                                "readiness": 5,
                            },
                            "blocking_reasons": ["judge_rejected"],
                            "rationale": "Not ready for handoff.",
                        }
                    ]
                },
            ]
        )
        with (
            patch("feedback_loop.cli._linear_memory_client_for_config", return_value=linear),
            patch("feedback_loop.pipeline.harvest.harvest_pr", return_value=[]),
            patch("feedback_loop.pipeline.harvest.normalize_signals", return_value=[signal_item]),
            patch(
                "feedback_loop.pipeline.llm_classify.classify_signals",
                return_value=ClassifyStageResult(signals=[signal_item]),
            ),
            patch(
                "feedback_loop.pipeline.llm_cluster.cluster_signals",
                return_value=LlmClusterStageResult(clusters=[cluster_item]),
            ),
            patch(
                "feedback_loop.pipeline.llm_evaluator._client_from_config",
                return_value=client,
            ),
            patch("feedback_loop.pipeline.emit.emit", return_value=[]) as emit_mock,
            patch("sys.stderr", new=stderr),
        ):
            rc = main(["run", "--pr", "https://github.com/squareup/wallet/pull/1", "--execute"])

        self.assertEqual(rc, 0)
        emit_mock.assert_not_called()
        self.assertIn("pending eval/PR-ready", stderr.getvalue())
        self.assertEqual(len(linear.written), 1)
        written_plan = linear.written[0][0]
        self.assertNotIn(BUILDERBOT_APPROVAL_LABEL, written_plan.labels)
        self.assertIn("Eval state: `eval_failed`", written_plan.description)
        self.assertIn("judge_rejected", written_plan.description)

    def test_execute_fails_when_linear_memory_is_unavailable(self):
        stderr = io.StringIO()
        with (
            patch("feedback_loop.cli._linear_memory_client_for_config", return_value=None),
            patch("feedback_loop.pipeline.harvest.harvest_pr", return_value=[]),
            patch("feedback_loop.pipeline.emit.emit", return_value=[]) as emit_mock,
            patch("sys.stderr", new=stderr),
        ):
            rc = main(["run", "--pr", "https://github.com/squareup/wallet/pull/1", "--execute"])

        self.assertEqual(rc, 3)
        emit_mock.assert_not_called()
        self.assertIn("Linear memory unavailable", stderr.getvalue())

    def test_backfill_classifies_the_whole_window_once(self):
        captured: dict = {}

        def fake_classify(cfg, client, signals, pr_facts, **kwargs):
            captured["classify_signals"] = list(signals)
            return ClassifyStageResult(signals=list(signals))

        def fake_cluster(cfg, client, classified, read_result):
            captured["cluster_signals"] = list(classified)
            return LlmClusterStageResult(clusters=[])

        first_pr = [
            raw_signal(
                "review_comment",
                "review_comment:1",
                pr_number=101,
                body="Please add a regression test here.",
                created_at="2026-05-04T01:00:00Z",
            )
        ]
        second_pr = [
            raw_signal(
                "review_comment",
                "review_comment:2",
                pr_number=202,
                body="Please add a regression test for this branch.",
                created_at="2026-05-05T01:00:00Z",
            )
        ]

        stdout = io.StringIO()
        with (
            patch(
                "feedback_loop.pipeline.harvest.list_merged_prs",
                return_value=[
                    "https://github.com/squareup/wallet/pull/101",
                    "https://github.com/squareup/wallet/pull/202",
                ],
            ),
            patch(
                "feedback_loop.pipeline.harvest.harvest_pr",
                side_effect=[first_pr, second_pr],
            ) as harvest_mock,
            patch("feedback_loop.pipeline.llm_classify.classify_signals", side_effect=fake_classify),
            patch("feedback_loop.pipeline.llm_cluster.cluster_signals", side_effect=fake_cluster),
            patch(
                "feedback_loop.pipeline.llm_evaluator._client_from_config",
                return_value=FakeLlmClient([{"learnings": []}]),
            ),
            patch("sys.stdout", new=stdout),
        ):
            rc = main(["run", "--backfill", "--since", "2026-05-01", "--limit", "2"])

        self.assertEqual(rc, 0)
        self.assertEqual(harvest_mock.call_count, 2)
        # Classification and clustering see the whole bounded window in one pass.
        self.assertEqual(
            sorted(signal.pr_number for signal in captured["classify_signals"]),
            [101, 202],
        )
        self.assertEqual(
            sorted(signal.pr_number for signal in captured["cluster_signals"]),
            [101, 202],
        )

    def test_wrong_repo_pr_fails_before_harvest(self):
        rc = main(["run", "--pr", "https://github.com/squareup/not-wallet/pull/1"])

        self.assertEqual(rc, 2)


class TestEmitDryRun(unittest.TestCase):
    def test_emit_dry_run_allows_empty_plan(self):
        self.assertEqual(emit.emit(RunConfig(dry_run=True), []), [])


def approved_docs_client(
    cluster_item: Cluster,
    signal_item: NormalizedSignal,
    *,
    learning_id: str,
) -> FakeLlmClient:
    return FakeLlmClient(
        [
            {
                "learnings": [
                    {
                        "learning_id": learning_id,
                        "cluster_slug": cluster_item.slug,
                        "evidence_urls": [signal_item.source_url],
                        "evidence_summary": "Linked review evidence needs durable docs.",
                        "agent_miss": "The agent missed the durable documentation handoff.",
                        "human_standard": "Future agents should update the canonical docs page.",
                        "severity": "high",
                        "confidence": 0.9,
                        "affected_area": "automation",
                        "routes": [
                            {
                                "destination": "docs",
                                "role": "primary",
                                "summary": "Update the feedback-loop docs with the route handoff rule.",
                                "rationale": "The lesson is human-facing durable guidance.",
                                "target_artifacts": ["docs/docs/automation/feedback-loop.md"],
                                "validation_commands": ["python -m unittest discover tests"],
                                "file_changes": [
                                    {
                                        "path": "docs/docs/automation/feedback-loop.md",
                                        "content": "Feedback-loop docs update.\n",
                                    }
                                ],
                            }
                        ],
                    }
                ]
            },
            docs_planner_response(),
            {
                "evaluations": [
                    {
                        "proposal_id": f"llm:{learning_id}:docs",
                        "publishable": True,
                        "scores": {
                            "source_grounding": 5,
                            "actionability": 5,
                            "route_correctness": 5,
                            "noise_risk": 5,
                            "readiness": 5,
                        },
                        "blocking_reasons": [],
                        "rationale": "Ready for a focused docs PR.",
                    }
                ]
            },
        ]
    )


def docs_planner_response() -> dict:
    return {
        "summary": "Update the feedback-loop docs with the route handoff rule.",
        "target_artifacts": ["docs/docs/automation/feedback-loop.md"],
        "file_changes": [
            {
                "path": "docs/docs/automation/feedback-loop.md",
                "content": "Feedback-loop docs update.\n",
            }
        ],
        "validation_commands": ["python -m unittest discover tests"],
        "acceptance_criteria": [
            "The canonical docs page includes the exact route handoff rule."
        ],
        "false_positive_controls": [
            "Scope the docs patch to the linked evidence and canonical page."
        ],
        "implementation_notes": "Keep this as a docs-only patch.",
        "non_goals": ["Do not add tests or checks in this docs route."],
    }


if __name__ == "__main__":
    unittest.main()
