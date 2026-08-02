"""Tests for the LLM learning/evaluator stage."""

from __future__ import annotations

import json
import os
from pathlib import Path
from types import SimpleNamespace
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from feedback_loop.artifacts import write_run_bundle  # noqa: E402
from feedback_loop.cluster_memory import ClusterMemoryReadResult  # noqa: E402
from feedback_loop.config import RunConfig  # noqa: E402
from feedback_loop.linear_control import build_cluster_issue_plan  # noqa: E402
from feedback_loop.llm import FakeLlmClient, LlmClientError  # noqa: E402
from helpers_reality import FakeRepoReality  # noqa: E402
from feedback_loop.models import (  # noqa: E402
    Cluster,
    NormalizedSignal,
    RawSignal,
    ReplayCase,
    ReplayCommitRange,
)
from feedback_loop.pipeline.llm_evaluator import (  # noqa: E402
    FIXABLE_REPAIR_REASONS,
    MAX_LLM_CLUSTERS,
    REPAIR_REASON_GLOSSARY,
    evaluate_llm_learnings,
    llm_debug_artifact,
    llm_learnings_artifact,
    llm_proposal_eval_artifact,
)


class TestLlmEvaluator(unittest.TestCase):
    def test_route_intent_is_planned_judged_and_marked_pr_ready(self):
        item = cluster()
        client = FakeLlmClient(
            [
                extractor_response(
                    routes=[
                        route(
                            "docs",
                            summary="Document the feedback-loop route rationale for future agents.",
                            target="docs/docs/automation/feedback-loop.md",
                        )
                    ]
                ),
                planner_response(
                    "docs",
                    "docs/docs/automation/feedback-loop.md",
                    content="## Feedback loop\n\nDocument route handoff criteria.\n",
                ),
                judge_response(["llm:learn-1:docs"]),
            ]
        )

        result = evaluate_llm_learnings(
            RunConfig(extra={"llm_client": client, "repo_reality": fake_repo(), "replay_cases": ()}),
            clusters=[item],
            signals=item.signals,
            read_result=ClusterMemoryReadResult(status="skipped"),
        )

        self.assertEqual([request["task"] for request in client.requests], [
            "extract_learnings",
            "plan_route_patch",
            "judge_proposals",
        ])
        self.assertEqual(len(result.learnings), 1)
        self.assertEqual(len(result.proposals), 1)
        proposal = result.proposals[0]
        self.assertEqual(proposal.learning_id, "learn-1")
        self.assertEqual(proposal.eval_state, "pr_ready")
        self.assertEqual(proposal.file_changes[0].path, "docs/docs/automation/feedback-loop.md")
        self.assertEqual(result.summary["planner_calls"], 1)
        self.assertEqual(result.summary["judge_calls"], 1)
        self.assertEqual(result.summary["repair_calls"], 0)
        self.assertNotIn("file_changes", llm_learnings_artifact(result)[0]["routes"][0])
        # Planner and judge must always see existing guidance for dedup, and the judge gets the
        # deterministic runner-vs-inspection classification of validation commands.
        planner_input = client.requests[1]["input"]
        judge_input = client.requests[2]["input"]
        self.assertIn("existing_agents_checks", planner_input["existing_guidance"])
        self.assertIn("existing_agents_checks", judge_input["existing_guidance"])
        self.assertEqual(
            [item["kind"] for item in judge_input["validation_command_assessment"]],
            ["unknown"],
        )

    def test_route_proposal_preserves_cluster_source_urls(self):
        item = cluster()
        second_url = "https://github.com/squareup/wallet/pull/456#discussion_r2"
        item.source_urls.append(second_url)
        client = FakeLlmClient(
            [
                extractor_response(
                    evidence_urls=[item.source_urls[0]],
                    routes=[
                        route(
                            "docs",
                            summary="Document route evidence handling.",
                            target="docs/docs/automation/feedback-loop.md",
                        )
                    ],
                ),
                planner_response(
                    "docs",
                    "docs/docs/automation/feedback-loop.md",
                    content="## Feedback loop\n\nPreserve source links.\n",
                ),
                judge_response(["llm:learn-1:docs"]),
            ]
        )

        result = evaluate_llm_learnings(
            RunConfig(extra={"llm_client": client, "repo_reality": fake_repo(), "replay_cases": ()}),
            clusters=[item],
            signals=item.signals,
            read_result=ClusterMemoryReadResult(status="skipped"),
        )

        proposal = result.proposals[0]
        self.assertEqual(proposal.evidence_urls, [item.source_urls[0]])
        self.assertEqual(proposal.cluster.source_urls, item.source_urls)

    def test_planner_missing_file_changes_blocks_before_judge_after_one_repair(self):
        item = cluster()
        client = FakeLlmClient(
            [
                extractor_response(
                    routes=[route("docs", summary="Update canonical feedback-loop docs.", target="docs/docs/automation/feedback-loop.md")]
                ),
                planner_response(
                    "docs",
                    "docs/docs/automation/feedback-loop.md",
                    file_changes=[],
                ),
                planner_response(
                    "docs",
                    "docs/docs/automation/feedback-loop.md",
                    file_changes=[],
                ),
            ]
        )

        result = evaluate_llm_learnings(
            RunConfig(extra={"llm_client": client, "repo_reality": fake_repo(), "replay_cases": ()}),
            clusters=[item],
            signals=item.signals,
            read_result=ClusterMemoryReadResult(status="skipped"),
        )

        proposal = result.proposals[0]
        self.assertEqual([request["task"] for request in client.requests], [
            "extract_learnings",
            "plan_route_patch",
            "repair_route_patch",
        ])
        self.assertEqual(proposal.eval_state, "eval_failed")
        self.assertIn("missing_file_changes", proposal.eval_artifact.blocking_reasons)
        self.assertEqual(result.summary["judge_calls"], 0)
        self.assertEqual(result.summary["repair_calls"], 1)

    def test_planner_transport_failure_retries_once_and_is_not_proposal_quality(self):
        item = cluster()
        client = FakeLlmClient(
            [
                extractor_response(
                    routes=[route("docs", summary="Update canonical feedback-loop docs.", target="docs/docs/automation/feedback-loop.md")]
                ),
                LlmClientError("Command timed out after 120 seconds"),
                LlmClientError("LLM adapter returned empty output"),
            ]
        )

        result = evaluate_llm_learnings(
            RunConfig(extra={"llm_client": client, "repo_reality": fake_repo(), "replay_cases": ()}),
            clusters=[item],
            signals=item.signals,
            read_result=ClusterMemoryReadResult(status="skipped"),
        )

        proposal = result.proposals[0]
        record = llm_proposal_eval_artifact(result)[0]
        debug = llm_debug_artifact(result)
        self.assertEqual([request["task"] for request in client.requests], [
            "extract_learnings",
            "plan_route_patch",
            "plan_route_patch",
        ])
        self.assertEqual(proposal.eval_state, "eval_failed")
        self.assertEqual(proposal.eval_artifact.blocking_reasons, ("planner_transport_error",))
        self.assertEqual(record["planner_attempts"], 2)
        self.assertTrue(record["planner_retry_attempted"])
        self.assertEqual(record["planner_error_kind"], "planner_transport_error")
        self.assertEqual(result.summary["planner_calls"], 2)
        self.assertEqual(debug["planner"]["error_kinds"]["planner_transport_error"], 1)

    def test_malformed_planner_schema_is_normalized_once_before_blocking(self):
        item = cluster()
        client = FakeLlmClient(
            [
                extractor_response(
                    routes=[route("docs", summary="Update canonical feedback-loop docs.", target="docs/docs/automation/feedback-loop.md")]
                ),
                {"status": "planned", "planned_route": {"target_artifacts": ["docs/docs/automation/feedback-loop.md"]}},
                planner_response(
                    "docs",
                    "docs/docs/automation/feedback-loop.md",
                    content="## Feedback loop\n\nNormalized planner output.\n",
                ),
                judge_response(["llm:learn-1:docs"]),
            ]
        )

        result = evaluate_llm_learnings(
            RunConfig(extra={"llm_client": client, "repo_reality": fake_repo(), "replay_cases": ()}),
            clusters=[item],
            signals=item.signals,
            read_result=ClusterMemoryReadResult(status="skipped"),
        )

        record = llm_proposal_eval_artifact(result)[0]
        self.assertEqual([request["task"] for request in client.requests], [
            "extract_learnings",
            "plan_route_patch",
            "normalize_route_plan_format",
            "judge_proposals",
        ])
        self.assertEqual(result.proposals[0].eval_state, "pr_ready")
        self.assertEqual(record["planner_status"], "planned")
        self.assertEqual(record["planner_attempts"], 2)
        self.assertTrue(record["planner_retry_attempted"])

    def test_planner_not_justified_blocks_without_judge_or_emit_ready(self):
        item = cluster()
        client = FakeLlmClient(
            [
                extractor_response(
                    routes=[route("docs", summary="Update canonical feedback-loop docs.", target="docs/docs/automation/feedback-loop.md")]
                ),
                {
                    "status": "not_justified",
                    "planned_route": None,
                    "not_justified_reason": "The evidence does not support an exact docs edit.",
                },
            ]
        )

        result = evaluate_llm_learnings(
            RunConfig(extra={"llm_client": client, "repo_reality": fake_repo(), "replay_cases": ()}),
            clusters=[item],
            signals=item.signals,
            read_result=ClusterMemoryReadResult(status="skipped"),
        )

        proposal = result.proposals[0]
        record = llm_proposal_eval_artifact(result)[0]
        self.assertEqual(proposal.eval_state, "eval_failed")
        self.assertEqual(proposal.eval_artifact.blocking_reasons, ("planner_not_justified",))
        self.assertEqual(record["planner_status"], "not_justified")
        self.assertEqual(result.summary["judge_calls"], 0)
        self.assertEqual(result.summary["errors"], 0)

    def test_planner_placeholder_artifacts_are_repaired_before_policy_mismatch_blocks(self):
        item = cluster()
        client = FakeLlmClient(
            [
                extractor_response(
                    routes=[route("test_or_linter", summary="Add a regression test for route planning.", target="app/**/*.kt")]
                ),
                planner_response(
                    "test_or_linter",
                    "app/**/*.kt",
                    content="fun test() {}\n",
                    acceptance=[
                        "Failing scenario: route plans with placeholders should fail local validation.",
                        "Expected assertion: assert the placeholder path is rejected.",
                    ],
                ),
                planner_response(
                    "test_or_linter",
                    "app/**/*.kt",
                    content="fun test() {}\n",
                    acceptance=[
                        "Failing scenario: route plans with placeholders should fail local validation.",
                        "Expected assertion: assert the placeholder path is rejected.",
                    ],
                ),
            ]
        )

        result = evaluate_llm_learnings(
            RunConfig(extra={"llm_client": client, "repo_reality": fake_repo(), "replay_cases": ()}),
            clusters=[item],
            signals=item.signals,
            read_result=ClusterMemoryReadResult(status="skipped"),
        )

        proposal = result.proposals[0]
        self.assertEqual([request["task"] for request in client.requests], [
            "extract_learnings",
            "plan_route_patch",
            "repair_route_patch",
        ])
        self.assertEqual(proposal.eval_state, "eval_failed")
        self.assertIn("broad_target_artifacts", proposal.eval_artifact.blocking_reasons)
        self.assertEqual(result.summary["judge_calls"], 0)
        self.assertEqual(result.summary["repair_calls"], 1)

    def test_agents_check_run_check_validation_is_repaired_to_repo_valid_command(self):
        item = cluster(destination="agents_check")
        client = FakeLlmClient(
            [
                extractor_response(
                    routes=[
                        route(
                            "agents_check",
                            summary="Add a repo-local review check for route planning misses.",
                            target=".agents/checks/route-planning.md",
                        )
                    ]
                ),
                planner_response(
                    "agents_check",
                    ".agents/checks/route-planning.md",
                    validation=["python .agents/checks/run_check.py"],
                ),
                planner_response(
                    "agents_check",
                    ".agents/checks/route-planning.md",
                    validation=['sq agents review "main...HEAD"'],
                ),
                judge_response(["llm:learn-1:agents_check"]),
            ]
        )

        result = evaluate_llm_learnings(
            RunConfig(extra={"llm_client": client, "repo_reality": fake_repo(), "replay_cases": ()}),
            clusters=[item],
            signals=item.signals,
            read_result=ClusterMemoryReadResult(status="skipped"),
        )

        proposal = result.proposals[0]
        record = llm_proposal_eval_artifact(result)[0]
        self.assertEqual(proposal.eval_state, "pr_ready")
        self.assertEqual(result.summary["repair_calls"], 1)
        self.assertEqual(
            record["repair_blocking_reasons"],
            ["missing_agents_check_validation", "nonexistent_validation_path"],
        )
        self.assertEqual(proposal.validation_commands, ['sq agents review "main...HEAD"'])

    def test_weak_concrete_plan_reaches_judge_and_blocks_on_low_score(self):
        item = cluster()
        client = FakeLlmClient(
            [
                extractor_response(
                    routes=[route("docs", summary="Update feedback-loop docs with route criteria.", target="docs/docs/automation/feedback-loop.md")]
                ),
                planner_response(
                    "docs",
                    "docs/docs/automation/feedback-loop.md",
                    content="## Feedback loop\n\nConcrete but weakly evidenced docs update.\n",
                ),
                judge_response(
                    ["llm:learn-1:docs"],
                    publishable=False,
                    scores={"source_grounding": 3},
                    blocking_reasons=["weak_evidence"],
                    rationale="The plan is concrete but evidence is weak.",
                ),
            ]
        )

        result = evaluate_llm_learnings(
            RunConfig(extra={"llm_client": client, "repo_reality": fake_repo(), "replay_cases": ()}),
            clusters=[item],
            signals=item.signals,
            read_result=ClusterMemoryReadResult(status="skipped"),
        )

        proposal = result.proposals[0]
        self.assertEqual(result.summary["judge_calls"], 1)
        self.assertEqual(result.summary["repair_calls"], 0)
        self.assertIn("source_grounding_below_threshold", proposal.eval_artifact.blocking_reasons)
        self.assertNotIn("planner_schema_error", proposal.eval_artifact.blocking_reasons)

    def test_judge_rejects_under_specified_plan_then_repair_reaches_pr_ready(self):
        item = cluster()
        client = FakeLlmClient(
            [
                extractor_response(
                    routes=[route("docs", summary="Update feedback-loop docs with concrete handoff criteria.", target="docs/docs/automation/feedback-loop.md")]
                ),
                planner_response(
                    "docs",
                    "docs/docs/automation/feedback-loop.md",
                    content="## Feedback loop\n\nInitial underspecified note.\n",
                ),
                judge_response(
                    ["llm:learn-1:docs"],
                    publishable=False,
                    scores={"readiness": 3},
                    blocking_reasons=["under_specified_plan"],
                    rationale="The proposed patch needs more concrete handoff details.",
                ),
                planner_response(
                    "docs",
                    "docs/docs/automation/feedback-loop.md",
                    content="## Feedback loop\n\nAdd concrete handoff criteria and link validation.\n",
                    acceptance=["Docs name the exact route handoff rule and link validation command."],
                ),
                judge_response(["llm:learn-1:docs"]),
            ]
        )

        result = evaluate_llm_learnings(
            RunConfig(extra={"llm_client": client, "repo_reality": fake_repo(), "replay_cases": ()}),
            clusters=[item],
            signals=item.signals,
            read_result=ClusterMemoryReadResult(status="skipped"),
        )

        proposal = result.proposals[0]
        eval_record = llm_proposal_eval_artifact(result)[0]
        self.assertEqual(proposal.eval_state, "pr_ready")
        self.assertEqual(result.summary["judge_calls"], 2)
        self.assertEqual(result.summary["repair_calls"], 1)
        self.assertTrue(eval_record["repair_attempted"])
        self.assertTrue(eval_record["repair_succeeded"])
        self.assertEqual(eval_record["repair_blocking_reasons"], ["under_specified_plan", "judge_not_publishable", "readiness_below_threshold"])

    def test_repair_is_not_attempted_for_world_model_research_only(self):
        item = cluster(destination="world_model")
        client = FakeLlmClient(
            [
                extractor_response(
                    routes=[
                        {
                            "destination": "world_model",
                            "role": "primary",
                            "summary": "Record durable feedback-loop ownership knowledge for future reasoning.",
                            "rationale": "The knowledge spans more than a repo-local instruction.",
                        }
                    ]
                ),
                planner_response(
                    "world_model",
                    "world model store",
                    file_changes=[],
                    validation=["Validate with the owning source document."],
                ),
            ]
        )

        result = evaluate_llm_learnings(
            RunConfig(extra={"llm_client": client, "repo_reality": fake_repo(), "replay_cases": ()}),
            clusters=[item],
            signals=item.signals,
            read_result=ClusterMemoryReadResult(status="skipped"),
        )

        proposal = result.proposals[0]
        self.assertEqual(proposal.eval_state, "eval_failed")
        self.assertIn("world_model_research_only", proposal.eval_artifact.blocking_reasons)
        self.assertEqual(proposal.eval_artifact.failure_destination, "research")
        self.assertEqual(result.summary["judge_calls"], 0)
        self.assertEqual(result.summary["repair_calls"], 0)

    def test_repair_is_attempted_at_most_once(self):
        item = cluster()
        client = FakeLlmClient(
            [
                extractor_response(
                    routes=[route("docs", summary="Update canonical feedback-loop docs.", target="docs/docs/automation/feedback-loop.md")]
                ),
                planner_response(
                    "docs",
                    "docs/docs/automation/feedback-loop.md",
                    file_changes=[],
                ),
                planner_response(
                    "docs",
                    "docs/docs/automation/feedback-loop.md",
                    file_changes=[],
                ),
            ]
        )

        result = evaluate_llm_learnings(
            RunConfig(extra={"llm_client": client, "repo_reality": fake_repo(), "replay_cases": ()}),
            clusters=[item],
            signals=item.signals,
            read_result=ClusterMemoryReadResult(status="skipped"),
        )

        self.assertEqual([request["task"] for request in client.requests], [
            "extract_learnings",
            "plan_route_patch",
            "repair_route_patch",
        ])
        self.assertEqual(result.summary["repair_calls"], 1)
        self.assertEqual(result.proposals[0].eval_state, "eval_failed")

    def test_repair_request_includes_blocking_reason_glossary(self):
        item = cluster()
        client = FakeLlmClient(
            [
                extractor_response(
                    routes=[route("docs", summary="Update canonical feedback-loop docs.", target="docs/docs/automation/feedback-loop.md")]
                ),
                planner_response("docs", "docs/docs/automation/feedback-loop.md", file_changes=[]),
                planner_response("docs", "docs/docs/automation/feedback-loop.md", file_changes=[]),
            ]
        )

        evaluate_llm_learnings(
            RunConfig(extra={"llm_client": client, "repo_reality": fake_repo(), "replay_cases": ()}),
            clusters=[item],
            signals=item.signals,
            read_result=ClusterMemoryReadResult(status="skipped"),
        )

        repair_request = client.requests[2]
        self.assertEqual(repair_request["task"], "repair_route_patch")
        glossary = repair_request["input"]["blocking_reason_glossary"]
        self.assertIn("missing_file_changes", glossary)
        for reason in repair_request["input"]["blocking_reasons"]:
            if reason in FIXABLE_REPAIR_REASONS:
                self.assertIn(reason, glossary)

    def test_repair_reason_glossary_covers_all_fixable_reasons(self):
        self.assertEqual(set(REPAIR_REASON_GLOSSARY), set(FIXABLE_REPAIR_REASONS))

    def test_unified_diff_mode_is_preserved_in_artifacts_and_linear_context(self):
        item = cluster()
        client = FakeLlmClient(
            [
                extractor_response(
                    routes=[route("docs", summary="Update feedback-loop docs with route criteria.", target="docs/docs/automation/feedback-loop.md")]
                ),
                planner_response(
                    "docs",
                    "docs/docs/automation/feedback-loop.md",
                    mode="unified_diff",
                    content="--- a/docs/docs/automation/feedback-loop.md\n+++ b/docs/docs/automation/feedback-loop.md\n@@\n+Route criteria.\n",
                ),
                judge_response(["llm:learn-1:docs"]),
            ]
        )
        result = evaluate_llm_learnings(
            RunConfig(extra={"llm_client": client, "repo_reality": fake_repo(), "replay_cases": ()}),
            clusters=[item],
            signals=item.signals,
            read_result=ClusterMemoryReadResult(status="skipped"),
        )
        proposal = result.proposals[0]

        with tempfile.TemporaryDirectory() as tmp:
            output_dir = Path(tmp)
            write_run_bundle(
                RunConfig(dry_run=True, output_dir=str(output_dir), extra={}),
                mode="pr",
                pr_urls=[],
                counts={},
                proposal_eval=result.summary,
                proposal_evals=llm_proposal_eval_artifact(result),
                llm_learnings=llm_learnings_artifact(result),
                llm_debug={},
                triage_report=triage_report(),
                full_triage_report=triage_report(),
                proposals=[proposal],
                blocked_proposals=[],
                emit_results=[],
            )
            proposals_json = json.loads((output_dir / "proposals.json").read_text())

        plan = build_cluster_issue_plan(
            proposal,
            trigger_builderbot=True,
            draft_pr_title="Feedback loop: docs guardrail",
            draft_pr_body="## Summary\nUse the planned diff.",
        )

        self.assertEqual(proposals_json[0]["file_changes"][0]["mode"], "unified_diff")
        self.assertIn("- Mode: `unified_diff`", plan.description)

    def test_replay_gate_runs_between_planner_and_judge_for_mechanical_routes(self):
        item = cluster(destination="agents_check")
        case = replay_corpus_case("case-route")
        client = FakeLlmClient(
            [
                extractor_response(
                    routes=[
                        route(
                            "agents_check",
                            summary="Add a repo-local review check for route planning misses.",
                            target=".agents/checks/route-planning.md",
                        )
                    ]
                ),
                planner_response("agents_check", ".agents/checks/route-planning.md"),
                replay_runner_response(case, caught=True),
                judge_response(["llm:learn-1:agents_check"]),
            ]
        )

        result = evaluate_llm_learnings(
            RunConfig(
                extra={
                    "llm_client": client,
                    "repo_reality": fake_repo(),
                    "replay_cases": (case,),
                    "git_client": FakeGit(commits={"aaaa111", "bbbb222"}),
                }
            ),
            clusters=[item],
            signals=item.signals,
            read_result=ClusterMemoryReadResult(status="skipped"),
        )

        self.assertEqual([request["task"] for request in client.requests], [
            "extract_learnings",
            "plan_route_patch",
            "replay_check_against_historical_diff",
            "judge_proposals",
        ])
        proposal = result.proposals[0]
        record = llm_proposal_eval_artifact(result)[0]
        self.assertEqual(proposal.eval_state, "pr_ready")
        self.assertEqual(record["replay_status"], "passed")
        self.assertEqual(record["replay_matched_case_ids"], ["case-route"])
        self.assertEqual(result.summary["replay_calls"], 1)
        self.assertEqual(proposal.eval_artifact.matched_replay_case_ids, ("case-route",))
        self.assertIn("## Replay gate", proposal.eval_artifact.rubric_markdown)
        judge_input = client.requests[3]["input"]
        self.assertEqual(judge_input["replay_results"]["status"], "passed")
        self.assertEqual(len(result.replay_artifacts), 1)

    def test_replay_miss_triggers_repair_then_replay_reruns_before_judge(self):
        item = cluster(destination="agents_check")
        case = replay_corpus_case("case-route")
        client = FakeLlmClient(
            [
                extractor_response(
                    routes=[
                        route(
                            "agents_check",
                            summary="Add a repo-local review check for route planning misses.",
                            target=".agents/checks/route-planning.md",
                        )
                    ]
                ),
                planner_response("agents_check", ".agents/checks/route-planning.md"),
                {"findings": []},
                planner_response("agents_check", ".agents/checks/route-planning.md"),
                replay_runner_response(case, caught=True),
                judge_response(["llm:learn-1:agents_check"]),
            ]
        )

        result = evaluate_llm_learnings(
            RunConfig(
                extra={
                    "llm_client": client,
                    "repo_reality": fake_repo(),
                    "replay_cases": (case,),
                    "git_client": FakeGit(commits={"aaaa111", "bbbb222"}),
                }
            ),
            clusters=[item],
            signals=item.signals,
            read_result=ClusterMemoryReadResult(status="skipped"),
        )

        self.assertEqual([request["task"] for request in client.requests], [
            "extract_learnings",
            "plan_route_patch",
            "replay_check_against_historical_diff",
            "repair_route_patch",
            "replay_check_against_historical_diff",
            "judge_proposals",
        ])
        proposal = result.proposals[0]
        record = llm_proposal_eval_artifact(result)[0]
        self.assertEqual(proposal.eval_state, "pr_ready")
        self.assertEqual(record["repair_blocking_reasons"], ["replay_recall_below_threshold"])
        self.assertEqual(result.summary["replay_calls"], 2)
        # The repair prompt sees the runner's actual findings and diffs, never the expected answer.
        repair_input = client.requests[3]["input"]
        self.assertEqual(repair_input["replay_results"]["status"], "failed")
        self.assertNotIn("expected_finding", str(repair_input))

    def test_replay_miss_without_repair_budget_blocks_before_judge(self):
        item = cluster(destination="agents_check")
        case = replay_corpus_case("case-route")
        client = FakeLlmClient(
            [
                extractor_response(
                    routes=[
                        route(
                            "agents_check",
                            summary="Add a repo-local review check for route planning misses.",
                            target=".agents/checks/route-planning.md",
                        )
                    ]
                ),
                planner_response("agents_check", ".agents/checks/route-planning.md"),
                {"findings": []},
                planner_response("agents_check", ".agents/checks/route-planning.md"),
                {"findings": []},
            ]
        )

        result = evaluate_llm_learnings(
            RunConfig(
                extra={
                    "llm_client": client,
                    "repo_reality": fake_repo(),
                    "replay_cases": (case,),
                    "git_client": FakeGit(commits={"aaaa111", "bbbb222"}),
                }
            ),
            clusters=[item],
            signals=item.signals,
            read_result=ClusterMemoryReadResult(status="skipped"),
        )

        proposal = result.proposals[0]
        record = llm_proposal_eval_artifact(result)[0]
        self.assertEqual(proposal.eval_state, "eval_failed")
        self.assertEqual(
            proposal.eval_artifact.blocking_reasons,
            ("replay_recall_below_threshold",),
        )
        self.assertEqual(record["replay_status"], "failed")
        self.assertEqual(result.summary["judge_calls"], 0)

    def test_invalid_json_response_is_blocked_as_eval_artifact(self):
        result = evaluate_llm_learnings(
            RunConfig(extra={"llm_client": FakeLlmClient(["not-json"]), "repo_reality": fake_repo(), "replay_cases": ()}),
            clusters=[cluster()],
            signals=[],
            read_result=ClusterMemoryReadResult(status="skipped"),
        )

        self.assertEqual(result.proposals, ())
        self.assertEqual(result.eval_records[0].blocking_reasons, ("invalid_extractor_response",))
        self.assertEqual(result.summary["errors"], 1)

    def test_non_boolean_judge_publishable_blocks_as_invalid_response(self):
        item = cluster()
        client = FakeLlmClient(
            [
                extractor_response(
                    routes=[
                        route(
                            "docs",
                            summary="Update canonical feedback-loop docs.",
                            target="docs/docs/automation/feedback-loop.md",
                        )
                    ]
                ),
                planner_response(
                    "docs",
                    "docs/docs/automation/feedback-loop.md",
                    content="## Feedback loop\n\nDocument route handoff criteria.\n",
                ),
                {
                    "evaluations": [
                        {
                            "proposal_id": "llm:learn-1:docs",
                            "publishable": "false",
                            "scores": {
                                "source_grounding": 5,
                                "actionability": 5,
                                "route_correctness": 5,
                                "noise_risk": 5,
                                "readiness": 5,
                            },
                            "blocking_reasons": [],
                            "rationale": "Malformed string boolean.",
                        }
                    ]
                },
            ]
        )

        result = evaluate_llm_learnings(
            RunConfig(extra={"llm_client": client, "repo_reality": fake_repo(), "replay_cases": ()}),
            clusters=[item],
            signals=item.signals,
            read_result=ClusterMemoryReadResult(status="skipped"),
        )

        proposal = result.proposals[0]
        assert proposal.eval_artifact is not None
        self.assertEqual(proposal.eval_state, "eval_failed")
        self.assertEqual(proposal.eval_artifact.blocking_reasons, ("invalid_judge_response",))
        self.assertIn("publishable must be a boolean", result.errors[0])

    def test_extractor_context_over_cap_blocks_before_llm(self):
        clusters = [cluster() for _ in range(MAX_LLM_CLUSTERS + 1)]
        client = FakeLlmClient([])

        result = evaluate_llm_learnings(
            RunConfig(extra={"llm_client": client, "repo_reality": fake_repo(), "replay_cases": ()}),
            clusters=clusters,
            signals=[item.signals[0] for item in clusters],
            read_result=ClusterMemoryReadResult(status="skipped"),
        )

        self.assertEqual(client.requests, [])
        self.assertEqual(result.proposals, ())
        self.assertEqual(result.eval_records[0].blocking_reasons, ("llm_context_truncated",))
        self.assertIn("clusters > cap", result.errors[0])
        self.assertEqual(result.summary["errors"], 1)


def fake_repo() -> FakeRepoReality:
    return FakeRepoReality(
        files={
            "docs/docs/automation/feedback-loop.md": "# Feedback loop\n",
            ".ai/AGENTS.md": "# Agent rules\n",
        },
        dirs={".agents/checks", ".ai/skills"},
    )


def cluster(destination: str = "test_or_linter") -> Cluster:
    item = signal(destination=destination)
    return Cluster(
        slug=f"miss:automation:route-handling:{destination}",
        signals=[item],
        area="automation",
        severity="high",
        frequency=2,
        rank=8.0,
        suggested_destination=destination,
        summary="Repeated route-handling misses in the feedback loop.",
        source_urls=[item.source_url],
    )


def signal(destination: str = "test_or_linter") -> NormalizedSignal:
    raw = RawSignal(
        kind="review_comment",
        source_id="review_comment:1",
        source_url="https://github.com/squareup/wallet/pull/123#discussion_r1",
        repo="squareup/wallet",
        pr_number=123,
        captured_at="2026-06-09T00:00:00Z",
        body="Please add route coverage and document the durable rationale.",
        path="automation/feedback-loop/feedback_loop/pipeline/llm_evaluator.py",
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
        area="automation",
        primary_class="miss",
        severity="high",
        confidence=0.9,
        suggested_destination=destination,
    )


def extractor_response(
    *,
    routes: list[dict],
    evidence_urls: list[str] | None = None,
) -> dict:
    evidence = evidence_urls or ["https://github.com/squareup/wallet/pull/123#discussion_r1"]
    destination = routes[0]["destination"] if routes else "test_or_linter"
    return {
        "learnings": [
            {
                "learning_id": "learn-1",
                "cluster_slug": f"miss:automation:route-handling:{destination}",
                "evidence_urls": evidence,
                "evidence_summary": "Linked review evidence asks for route coverage and durable rationale.",
                "agent_miss": "The agent missed that route handling needed durable feedback-loop coverage.",
                "human_standard": "Future agents should add source-grounded route coverage before handoff.",
                "severity": "high",
                "confidence": 0.91,
                "affected_area": "automation",
                "routes": routes,
            }
        ]
    }


def route(
    destination: str,
    *,
    role: str = "primary",
    summary: str,
    target: str,
) -> dict:
    return {
        "destination": destination,
        "role": role,
        "summary": summary,
        "rationale": "This route creates durable future-agent coverage.",
        "target_artifacts": [target],
    }


def planner_response(
    destination: str,
    path: str,
    *,
    mode: str = "create_or_update",
    content: str = "generated proposal body\n",
    file_changes: list[dict] | None = None,
    validation: list[str] | None = None,
    acceptance: list[str] | None = None,
    false_positive_controls: list[str] | None = None,
) -> dict:
    if destination == "agents_check" and content == "generated proposal body\n":
        content = agents_check_content()
    changes = [{"path": path, "mode": mode, "content": content}] if file_changes is None else file_changes
    validation_commands = validation or ["python -m unittest discover tests"]
    criteria = acceptance or ["The exact file change implements the durable route requirement."]
    controls = false_positive_controls or ["Limit the change to the linked evidence and exact target path."]
    if destination == "agents_check":
        validation_commands = validation or ['sq agents review "main...HEAD"']
        criteria = [
            "Positive fixture exercises the historical miss.",
            "Negative fixture shows valid changes pass.",
        ]
    if destination == "test_or_linter" and acceptance is None:
        criteria = [
            "Failing scenario: the replayed route miss fails before the patch.",
            "Expected assertion: assert the repaired route plan is accepted.",
        ]
    return {
        "status": "planned",
        "not_justified_reason": "",
        "planned_route": {
            "summary": f"Create a concrete {destination} patch plan for the feedback-loop learning.",
            "handoff_title": f"Create concrete {destination} feedback-loop guardrail",
            "target_artifacts": [path],
            "file_changes": changes,
            "validation_commands": validation_commands,
            "acceptance_criteria": criteria,
            "false_positive_controls": controls,
            "implementation_notes": "Keep the patch scoped to the exact route and evidence.",
            "non_goals": ["Do not broaden the route beyond this learning."],
        },
    }


def agents_check_content() -> str:
    return """---
name: route-planning
description: Flags route-planning misses from feedback-loop evidence.
severity-default: low
tools: [Read, Grep]
---

## Purpose

Catch repeated route-planning misses before generated handoff.

## Instructions

Review positive and negative fixture examples against the changed route plan.

## What to Flag

- Positive fixture evidence showing an unsupported route plan.

## What NOT to Flag

- Negative fixture evidence where the route is already exact and justified.
"""


def judge_response(
    proposal_ids: list[str],
    *,
    publishable: bool = True,
    scores: dict[str, int] | None = None,
    blocking_reasons: list[str] | None = None,
    rationale: str = "The proposal is source-grounded and ready.",
) -> dict:
    base_scores = {
        "source_grounding": 5,
        "actionability": 5,
        "route_correctness": 5,
        "noise_risk": 5,
        "readiness": 5,
    }
    base_scores.update(scores or {})
    return {
        "evaluations": [
            {
                "proposal_id": proposal_id,
                "publishable": publishable,
                "scores": base_scores,
                "blocking_reasons": blocking_reasons or [],
                "rationale": rationale,
            }
            for proposal_id in proposal_ids
        ]
    }


class FakeGit:
    def __init__(self, *, commits: set[str]):
        self.commits = commits

    def commit_exists(self, sha: str) -> bool:
        return sha in self.commits

    def diff_range(self, base: str, head: str, paths=(), *, max_bytes: int = 50_000) -> str:
        return "diff --git a/route.py b/route.py\n-old route plan\n+new route plan\n"


def replay_corpus_case(case_id: str) -> ReplayCase:
    return ReplayCase(
        case_id=case_id,
        repo="squareup/wallet",
        pr_number=99,
        pr_url="https://github.com/squareup/wallet/pull/99",
        commit_range=ReplayCommitRange(base="aaaa111", head="bbbb222"),
        changed_files=("automation/feedback-loop/feedback_loop/pipeline/llm_evaluator.py",),
        miss_class="miss",
        source_comment_url="https://github.com/squareup/wallet/pull/99#discussion_replay",
        expected_destination="agents_check",
        expected_finding="Route plans must be source-grounded before generated handoff.",
        summary="Historical route-planning miss without source grounding.",
    )


def replay_runner_response(case: ReplayCase, *, caught: bool) -> dict:
    if not caught:
        return {"findings": []}
    return {
        "findings": [
            {
                "case_id": case.case_id,
                "summary": "The diff hands off a route plan that is not source-grounded",
                "source_url": case.source_comment_url,
            }
        ]
    }


def triage_report() -> SimpleNamespace:
    return SimpleNamespace(markdown="", summary=[], comment_volume_summary={})


if __name__ == "__main__":
    unittest.main()
