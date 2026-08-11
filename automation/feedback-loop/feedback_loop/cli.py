"""CLI entrypoint + stage orchestration for the feedback loop.

Two entrypoints:
  run --pr <url>     process a single merged PR (the form the Builderbot/Blox trigger invokes)
  run --backfill     process a bounded historical window

Stage order:
  harvest -> normalize -> facts -> noise prefilter -> memory read -> llm classify -> llm cluster
  -> triage -> llm evaluator (promoted clusters only) -> readiness -> emit -> memory plan/write
  -> run bundle

Without an LLM client, dry-run degrades to a facts-only inventory and --execute refuses to run:
memory writes depend on LLM classification, so executing without one would write garbage.
"""

from __future__ import annotations

import argparse
from dataclasses import dataclass, replace
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import subprocess
import sys
from typing import Sequence

from .artifacts import BlockedProposal, write_run_bundle
from .cluster_memory import (
    ClusterMemoryPlanResult,
    ClusterMemoryReadResult,
    LinearMemoryUnavailable,
    SqAgentToolsLinearClient,
    attach_memory_context,
    cluster_memory_artifact,
    cluster_memory_summary,
    plan_cluster_memory_upserts,
    read_linear_memory,
    write_cluster_memory_upserts,
)
from .concurrency import harvest_max_workers, llm_max_workers, parallel_map_indexed
from .config import RunConfig
from .corpus_suggest import suggest_replay_cases
from .eval_gate import ProposalEvalBlocked, require_pr_ready
from .linear_control import BUILDERBOT_APPROVAL_LABEL
from .llm import ThrottledLlmClient
from .github import GitHubClient
from .models import REVIEW_ONLY_CLASSES, NormalizedSignal, PrFacts, Proposal
from .outcomes import outcome_artifact, reconcile_outcomes
from .pipeline import emit, facts, harvest, llm_classify, llm_cluster, llm_evaluator, noise, triage
from .pr_policy import PrPolicyBlocked
from .replay import load_replay_corpus
from .route_metadata import proposal_change_set_id
from .util import env_float

FEEDBACK_KINDS = frozenset({"issue_comment", "review_comment", "review", "bot_review"})

# Fraction of clusters that may be synthesized singletons (slug `unclustered-*`) before a run is
# treated as degenerate (clustering effectively failed). Tunable via env for backfills of unusual
# corpora; the default catches catastrophic failures like an all-singleton run.
MAX_SINGLETON_RATE_ENV = "FEEDBACK_LOOP_MAX_SINGLETON_RATE"
DEFAULT_MAX_SINGLETON_RATE = 0.5


def _max_singleton_rate() -> float:
    return env_float(MAX_SINGLETON_RATE_ENV, DEFAULT_MAX_SINGLETON_RATE, minimum=0.0, maximum=1.0)


@dataclass(frozen=True)
class ReadinessResult:
    ready: list[Proposal]
    blocked: list[BlockedProposal]


def _build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="feedback_loop",
        description="AI feedback loop: merged-PR review feedback -> durable guidance proposals.",
    )
    sub = parser.add_subparsers(dest="command", required=True)

    run = sub.add_parser("run", help="Run the pipeline (single PR or backfill).")
    mode = run.add_mutually_exclusive_group(required=True)
    mode.add_argument("--pr", dest="pr_url", help="Merged PR URL to process.")
    mode.add_argument(
        "--backfill",
        action="store_true",
        help="Process a bounded historical window (use --since/--until/--limit).",
    )
    run.add_argument("--repo", default="squareup/wallet", help="OWNER/REPO (default squareup/wallet).")
    run.add_argument("--since", help="Backfill start (ISO date).")
    run.add_argument("--until", help="Backfill end (ISO date).")
    run.add_argument("--limit", type=int, default=100, help="Max PRs for backfill (default 100).")
    run.add_argument(
        "--dry-run",
        dest="dry_run",
        action="store_true",
        default=True,
        help="No writes (default). Open no PRs/Linear issues/comments.",
    )
    run.add_argument(
        "--execute",
        dest="dry_run",
        action="store_false",
        help=(
            "Allow writes: creates Linear issues and auto-applies the Builderbot trigger label "
            "to pr_ready proposals. The human gate is draft-PR review/merge."
        ),
    )
    run.add_argument(
        "--output-dir",
        help="Write the run artifact bundle to this directory (execute mode defaults one).",
    )
    run.add_argument(
        "--repo-root",
        help="Local checkout root for plan reality checks (default: git toplevel, then CWD).",
    )

    reconcile = sub.add_parser(
        "reconcile-outcomes",
        help="Sync Linear issue states from draft-PR outcomes (merged->adopted, closed->rejected).",
    )
    reconcile.add_argument(
        "--repo", default="squareup/wallet", help="OWNER/REPO (default squareup/wallet)."
    )
    reconcile.add_argument(
        "--limit",
        type=int,
        default=50,
        help="Max in-flight issues to reconcile per run (default 50).",
    )
    reconcile.add_argument(
        "--dry-run",
        dest="dry_run",
        action="store_true",
        default=True,
        help="Report transitions without writing to Linear (default).",
    )
    reconcile.add_argument(
        "--execute",
        dest="dry_run",
        action="store_false",
        help="Apply state transitions and label removals to Linear.",
    )
    reconcile.add_argument(
        "--output-dir",
        help="Write outcome-reconcile.json to this directory.",
    )
    return parser


def _harvest_one(cfg: RunConfig, url: str) -> tuple[int, facts.FactsResult]:
    """Harvest, normalize, and attach facts for one PR; safe to run concurrently per PR."""
    raw = harvest.harvest_pr(cfg, url)
    normalized = harvest.normalize_signals(cfg, raw)
    return len(raw), facts.attach_facts(cfg, normalized)


def _detect_repo_root() -> str:
    try:
        completed = subprocess.run(
            ["git", "rev-parse", "--show-toplevel"],
            check=False,
            capture_output=True,
            text=True,
        )
    except OSError:
        return os.getcwd()
    toplevel = completed.stdout.strip()
    if completed.returncode != 0 or not toplevel:
        return os.getcwd()
    return toplevel


def _run(cfg: RunConfig, pr_urls: list[str], mode: str) -> int:
    linear_client = _linear_memory_client_for_config(cfg)
    read_result = read_linear_memory(
        dry_run=cfg.dry_run,
        reader=linear_client if _is_memory_reader(linear_client) else None,
    )

    harvested_count = 0
    enriched: list[NormalizedSignal] = []
    pr_facts: dict[int, PrFacts] = {}
    # PRs harvest independently (each call constructs its own GitHubClient); merging in pr_urls
    # order keeps downstream ordering identical to a sequential run, and the first HarvestError
    # in index order re-raises so backfill failure semantics are unchanged.
    harvest_slots = parallel_map_indexed(
        pr_urls,
        lambda url: _harvest_one(cfg, url),
        max_workers=harvest_max_workers(cfg),
        warm_first=False,
    )
    for slot in harvest_slots:
        raw_count, facts_result = slot.unwrap()
        harvested_count += raw_count
        enriched.extend(facts_result.signals)
        pr_facts.update(facts_result.pr_facts)

    kept, noise_signals = noise.prefilter(enriched)
    counts: dict[str, int] = {
        "harvested_signals": harvested_count,
        "normalized_signals": len(enriched),
        "noise_excluded_signals": len(noise_signals),
    }

    client = llm_evaluator._client_from_config(cfg)
    if client is None:
        return _degraded_run(
            cfg,
            mode=mode,
            pr_urls=pr_urls,
            signals=kept,
            noise_signals=noise_signals,
            read_result=read_result,
            counts=counts,
        )
    llm_workers = llm_max_workers(cfg)
    if llm_workers > 1:
        # One shared semaphore caps in-flight adapter subprocesses across every stage fan-out
        # (classify batches, evaluator routes, nested replay cases).
        client = ThrottledLlmClient(client, llm_workers)

    classify_result = llm_classify.classify_signals(
        cfg,
        client,
        kept,
        pr_facts,
        signal_bodies_by_id={signal.source_id: signal.body for signal in enriched},
    )
    cluster_result = llm_cluster.cluster_signals(
        cfg,
        client,
        classify_result.signals,
        read_result,
    )
    clusters = list(cluster_result.clusters)
    # Degenerate-clustering guardrail: a run where most clusters are synthesized singletons (slug
    # `unclustered-*`) or the cluster stage logged errors means clustering effectively failed —
    # promoting/emitting from it would pollute durable Linear state with frequency=1 noise. Fail
    # the run in --execute (abort before emit + Linear writes); only warn in --dry-run.
    synthesized_singletons = sum(
        1 for cluster in clusters if cluster.slug.startswith("unclustered-")
    )
    singleton_rate = (synthesized_singletons / len(clusters)) if clusters else 0.0
    max_singleton_rate = _max_singleton_rate()
    degenerate_clustering = bool(cluster_result.errors) or singleton_rate > max_singleton_rate
    abort_execute = degenerate_clustering and not cfg.dry_run
    if degenerate_clustering:
        outcome = (
            "aborting before emit and Linear writes"
            if abort_execute
            else "continuing dry-run for diagnostics"
        )
        sys.stderr.write(
            "feedback_loop: degenerate clustering — "
            f"{synthesized_singletons}/{len(clusters)} synthesized singletons "
            f"(rate {singleton_rate:.2f} > {max_singleton_rate:.2f}), "
            f"{len(cluster_result.errors)} cluster error(s); {outcome}\n"
        )
    audit_signals = noise_signals + [
        signal
        for signal in classify_result.signals
        if signal.kind in FEEDBACK_KINDS
        and (
            signal.is_excluded
            or signal.primary_class in REVIEW_ONLY_CLASSES
            or signal.primary_class is None
        )
    ]

    full_report = triage.build_triage_report(clusters, audit_signals=audit_signals)
    report = triage.build_triage_report(
        clusters,
        audit_signals=audit_signals,
        include_audit_only=False,
    )
    if not cfg.output_dir:
        _write_triage_report(cfg, full_report.markdown)

    promoted = [
        cluster
        for cluster in clusters
        if cluster.decision in {"promote", "convert_to_mechanical_check"}
    ]
    promoted_signals = [signal for cluster in promoted for signal in cluster.signals]
    if promoted and not abort_execute:
        llm_stage = llm_evaluator.evaluate_llm_learnings(
            cfg,
            clusters=promoted,
            signals=promoted_signals,
            read_result=read_result,
            client=client,
        )
    else:
        llm_stage = llm_evaluator.LlmEvaluatorStageResult()
    proposals = list(llm_stage.proposals)
    readiness = _pr_ready_proposals(cfg, proposals)
    suggested_cases = _suggest_corpus_cases(cfg, llm_stage, proposals, pr_facts)
    emit_results, emit_blocked = _build_emit_previews(
        cfg,
        readiness.ready,
        suggestions_by_learning={
            str(item["suggested_by_run"]["learning_id"]): item for item in suggested_cases
        },
    )
    blocked_proposals = [*readiness.blocked, *emit_blocked]
    memory_plan = plan_cluster_memory_upserts(
        clusters,
        proposals=proposals,
        emit_results=emit_results,
        existing_records=read_result.records,
        dry_run=cfg.dry_run,
        reconciliations=cluster_result.reconciliations,
        window={
            "pr_url": cfg.pr_url,
            "since": cfg.since,
            "until": cfg.until,
            "limit": cfg.limit,
        },
    )
    memory_plan = attach_memory_context(
        memory_plan,
        read_result=read_result,
        reconciliations=cluster_result.reconciliations,
        write_status="dry_run_preview" if cfg.dry_run else "skipped",
        warnings=cluster_result.warnings,
    )
    counts = {
        **counts,
        "classify_batches": classify_result.batch_count,
        "classify_failed_batches": classify_result.failed_batches,
        "classified_signals": len(
            [signal for signal in classify_result.signals if signal.primary_class is not None]
        ),
        "unclassified_signals": len(classify_result.unclassified_signal_ids),
        "actionable_signals": sum(len(cluster.signals) for cluster in clusters),
        "clusters": len(clusters),
        "clusters_matched_memory": sum(1 for cluster in clusters if cluster.matched_memory_key),
        "clusters_new": sum(1 for cluster in clusters if not cluster.matched_memory_key),
        "promoted_clusters": len(promoted),
        "gather_more_evidence_clusters": sum(
            1 for cluster in clusters if cluster.decision == "gather_more_evidence"
        ),
        "already_covered_clusters": sum(
            1 for cluster in clusters if cluster.decision == "already_covered"
        ),
        "cluster_llm_calls": cluster_result.llm_calls,
        "cluster_errors": len(cluster_result.errors),
        "synthesized_singletons": synthesized_singletons,
        "synthesized_singleton_rate": round(singleton_rate, 3),
        "degenerate_clustering": int(degenerate_clustering),
        "triage_clusters": len(full_report.summary),
        "triage_visible_clusters": len(report.summary),
        "generated_proposals": len(proposals),
        "llm_learnings": len(llm_stage.learnings),
        "pr_ready_proposals": len(readiness.ready),
        "blocked_proposals": len(blocked_proposals),
        "frequency_gate_blocked": sum(
            1
            for item in blocked_proposals
            if "frequency" in item.reason or "below_frequency_threshold" in item.reason
        ),
        "emit_preview_results": len(emit_results),
        "linear_memory_records_read": len(read_result.records),
        "linear_memory_upserts": len(memory_plan.upserts),
        "linear_memory_upserts_dropped": len(memory_plan.dropped_upserts),
        "replay_calls": llm_stage.replay_calls,
    }

    execution: dict | None = None
    if not cfg.dry_run and not abort_execute:
        if not _is_memory_writer(linear_client):
            raise LinearMemoryUnavailable("feedback-loop Linear memory writer is not wired")
        memory_plan = write_cluster_memory_upserts(
            memory_plan,
            linear_client,
            read_result.records,
        )
        emit_results = _attach_emit_linear_urls(emit_results, memory_plan)
        execution = _execution_summary(memory_plan, emit_results)

    # Surface cluster-stage failures alongside evaluator/planner errors so a silently degraded
    # clustering run is visible in llm-debug.json, not just in stderr.
    llm_debug = llm_evaluator.llm_debug_artifact(llm_stage)
    llm_debug["cluster_errors"] = list(cluster_result.errors)
    llm_debug["cluster_warnings"] = list(cluster_result.warnings)

    bundle_dir = write_run_bundle(
        cfg,
        mode=mode,
        pr_urls=pr_urls,
        counts=counts,
        proposal_eval=llm_stage.summary,
        proposal_evals=llm_evaluator.llm_proposal_eval_artifact(llm_stage),
        llm_learnings=llm_evaluator.llm_learnings_artifact(llm_stage),
        llm_debug=llm_debug,
        triage_report=report,
        full_triage_report=full_report,
        proposals=proposals,
        blocked_proposals=blocked_proposals,
        emit_results=emit_results,
        linear_memory=cluster_memory_artifact(memory_plan),
        linear_memory_summary=cluster_memory_summary(memory_plan),
        execution=execution,
        replay_gate=list(llm_stage.replay_artifacts),
        suggested_replay_cases=suggested_cases,
        classifications=llm_classify_artifact(classify_result),
        clusters_artifact=llm_cluster_artifact(clusters),
    )
    if execution is not None:
        _print_execute_summary(execution, counts, bundle_dir)
    if abort_execute:
        sys.stderr.write(
            f"feedback_loop: execute aborted on degenerate clustering; bundle written to {bundle_dir}\n"
        )
        return 2
    return 0


def llm_classify_artifact(result: llm_classify.ClassifyStageResult) -> list[dict]:
    """JSON-ready per-signal classification records for the run bundle."""
    artifact: list[dict] = []
    for signal in result.signals:
        if signal.kind not in FEEDBACK_KINDS:
            continue
        artifact.append(
            {
                "signal_id": signal.source_id,
                "pr_number": signal.pr_number,
                "primary_class": signal.primary_class,
                "severity": signal.severity,
                "confidence": signal.confidence,
                "suggested_destination": signal.suggested_destination,
                "exclusion_reason": None
                if signal.exclusion is None
                else signal.exclusion.reason,
                "manual_triage": signal.manual_triage,
                "resolution": None
                if signal.resolution is None
                else {
                    "state": signal.resolution.state,
                    "evidence_signal_ids": list(signal.resolution.evidence_signal_ids),
                    "coverage_paths": list(signal.resolution.coverage_paths),
                    "rationale": signal.resolution.rationale,
                },
                "rationale": signal.rationale,
            }
        )
    return artifact


def llm_cluster_artifact(clusters: list) -> list[dict]:
    """JSON-ready cluster records for the run bundle."""
    return [
        {
            "slug": cluster.slug,
            "title": cluster.title,
            "decision": cluster.decision,
            "severity": cluster.severity,
            "frequency": cluster.frequency,
            "rank": cluster.rank,
            "suggested_destination": cluster.suggested_destination,
            "area": cluster.area,
            "matched_memory_key": cluster.matched_memory_key,
            "matched_issue_url": cluster.matched_issue_url,
            "current_pr_numbers": list(cluster.current_pr_numbers),
            "merged_pr_numbers": list(cluster.merged_pr_numbers),
            "member_signal_ids": [signal.source_id for signal in cluster.signals],
            "summary": cluster.summary,
            "rationale": cluster.rationale,
        }
        for cluster in clusters
    ]


def _degraded_run(
    cfg: RunConfig,
    *,
    mode: str,
    pr_urls: list[str],
    signals: list[NormalizedSignal],
    noise_signals: list[NormalizedSignal],
    read_result: ClusterMemoryReadResult,
    counts: dict[str, int],
) -> int:
    if not cfg.dry_run:
        sys.stderr.write(
            "feedback_loop: --execute requires FEEDBACK_LOOP_LLM_COMMAND; refusing to write "
            "Linear memory without LLM classification\n"
        )
        return 3

    report = triage.facts_only_report(signals, noise_signals)
    if not cfg.output_dir:
        _write_triage_report(cfg, report.markdown)
    write_run_bundle(
        cfg,
        mode=mode,
        pr_urls=pr_urls,
        counts=counts,
        proposal_eval=None,
        proposal_evals=[],
        llm_learnings=[],
        llm_debug={"client_configured": False},
        triage_report=report,
        full_triage_report=report,
        proposals=[],
        blocked_proposals=[],
        emit_results=[],
        linear_memory={},
        linear_memory_summary={"read_status": read_result.status},
    )
    sys.stderr.write(
        "feedback_loop: no LLM client configured; wrote facts-only report and skipped "
        "classification, clustering, and proposals\n"
    )
    return 0


def _linear_memory_client_for_config(cfg: RunConfig) -> object | None:
    injected = cfg.extra.get("linear_memory_client")
    if injected is not None:
        return injected
    if not cfg.dry_run or os.environ.get("FEEDBACK_LOOP_LINEAR_READ") == "1":
        return SqAgentToolsLinearClient()
    return None


def _is_memory_reader(value: object | None) -> bool:
    return value is not None and hasattr(value, "read_cluster_memory")


def _is_memory_writer(value: object | None) -> bool:
    return value is not None and hasattr(value, "upsert_cluster_memory")


def _attach_emit_linear_urls(
    emit_results: list[emit.EmitResult],
    memory_plan: ClusterMemoryPlanResult,
) -> list[emit.EmitResult]:
    urls_by_key = {
        result.idempotency_key: result.issue_url
        for result in memory_plan.write_results
    }
    return [
        replace(
            result,
            linear_issue_url=urls_by_key.get(
                result.cluster_issue.idempotency_key,
                result.linear_issue_url,
            ),
        )
        for result in emit_results
    ]


def _build_emit_previews(
    cfg: RunConfig,
    ready: list[Proposal],
    *,
    suggestions_by_learning: dict[str, dict] | None = None,
) -> tuple[list[emit.EmitResult], list[BlockedProposal]]:
    """Build emit plans per proposal so one policy failure cannot abort the whole run."""
    suggestions = suggestions_by_learning or {}
    results: list[emit.EmitResult] = []
    blocked: list[BlockedProposal] = []
    for proposal in ready:
        try:
            results.extend(
                emit.build_emit_results(
                    cfg,
                    [proposal],
                    suggested_replay_case=suggestions.get(proposal.learning_id),
                )
            )
        except (ProposalEvalBlocked, PrPolicyBlocked) as err:
            blocked.append(BlockedProposal(proposal=proposal, reason=str(err)))
    return results, blocked


def _suggest_corpus_cases(
    cfg: RunConfig,
    llm_stage: llm_evaluator.LlmEvaluatorStageResult,
    proposals: list[Proposal],
    pr_facts: dict[int, PrFacts],
) -> list[dict]:
    existing_ids, existing_numbers = _existing_corpus_keys(cfg)
    return suggest_replay_cases(
        learnings=list(llm_stage.learnings),
        proposals=proposals,
        pr_facts_by_number=pr_facts,
        existing_case_ids=existing_ids,
        existing_pr_numbers=existing_numbers,
    )


def _existing_corpus_keys(cfg: RunConfig) -> tuple[frozenset[str], frozenset[int]]:
    if "replay_cases" in cfg.extra:
        cases = tuple(cfg.extra["replay_cases"] or ())
    else:
        try:
            cases = tuple(load_replay_corpus())
        except (OSError, ValueError):
            return frozenset(), frozenset()
    return (
        frozenset(case.case_id for case in cases),
        frozenset(case.pr_number for case in cases),
    )


def _execution_summary(
    memory_plan: ClusterMemoryPlanResult,
    emit_results: list[emit.EmitResult],
) -> dict:
    return {
        "linear_write_status": memory_plan.write_status,
        "issues_created": sum(1 for item in memory_plan.write_results if item.action == "create"),
        "issues_updated": sum(1 for item in memory_plan.write_results if item.action == "update"),
        "issue_urls": [item.issue_url for item in memory_plan.write_results if item.issue_url],
        "builderbot_triggered": [
            {
                "idempotency_key": result.cluster_issue.idempotency_key,
                "issue_url": result.linear_issue_url,
                "change_set_id": proposal_change_set_id(result.proposal),
            }
            for result in emit_results
            if BUILDERBOT_APPROVAL_LABEL in result.cluster_issue.labels
        ],
    }


def _print_execute_summary(
    execution: dict,
    counts: dict[str, int],
    bundle_dir: object,
) -> None:
    lines = [
        "feedback_loop: execute complete",
        (
            f"  linear issues created: {execution['issues_created']}, "
            f"updated: {execution['issues_updated']}"
        ),
        f"  pr-ready proposals: {counts.get('pr_ready_proposals', 0)}, "
        f"blocked: {counts.get('blocked_proposals', 0)}",
    ]
    lines.extend(f"  issue: {url}" for url in execution["issue_urls"])
    lines.extend(
        f"  builderbot trigger: {item['issue_url'] or item['idempotency_key']}"
        for item in execution["builderbot_triggered"]
    )
    if bundle_dir is not None:
        lines.append(f"  run bundle: {bundle_dir}")
    sys.stdout.write("\n".join(lines) + "\n")


def _pr_ready_proposals(cfg: RunConfig, proposals: list[Proposal]) -> ReadinessResult:
    ready: list[Proposal] = []
    blocked: list[BlockedProposal] = []
    for proposal in proposals:
        try:
            require_pr_ready(proposal)
        except ProposalEvalBlocked as err:
            blocked.append(BlockedProposal(proposal=proposal, reason=str(err)))
        else:
            ready.append(proposal)
    if blocked:
        _write_eval_blocked_summary(len(blocked))
    return ReadinessResult(ready=ready, blocked=blocked)


def _write_eval_blocked_summary(count: int) -> None:
    noun = "proposal" if count == 1 else "proposals"
    sys.stderr.write(
        f"feedback_loop: skipped {count} generated {noun} pending eval/PR-ready state\n"
    )


def _write_triage_report(cfg: RunConfig, markdown: str) -> None:
    if not cfg.dry_run:
        return
    sys.stdout.write(markdown)
    if not markdown.endswith("\n"):
        sys.stdout.write("\n")


def _default_execute_output_dir(args: argparse.Namespace) -> str | None:
    """Execute mode always records a run bundle; dry-run keeps stdout-only as the default."""
    if args.dry_run:
        return None
    timestamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    mode = "pr" if args.pr_url else "backfill"
    return os.path.join(".feedback-loop-runs", f"{mode}-{timestamp}")


def _reconcile_outcomes_command(cfg: RunConfig) -> int:
    reader = cfg.extra.get("linear_memory_client") or SqAgentToolsLinearClient()
    writer = None if cfg.dry_run else reader
    github = cfg.extra.get("github_client") or GitHubClient()
    result = reconcile_outcomes(cfg, reader=reader, writer=writer, github=github)

    if cfg.output_dir:
        output_dir = Path(cfg.output_dir)
        output_dir.mkdir(parents=True, exist_ok=True)
        (output_dir / "outcome-reconcile.json").write_text(
            json.dumps(outcome_artifact(result), indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )

    lines = [
        "feedback_loop: outcome reconcile "
        + ("preview" if result.dry_run else "complete"),
        f"  candidates: {result.counts.get('candidates', 0)}, "
        f"applied: {result.counts.get('applied', 0)}, "
        f"skipped: {result.counts.get('skipped', 0)}, "
        f"errors: {result.counts.get('errors', 0)}",
    ]
    for action in result.actions:
        marker = "applied" if action.applied else (action.skipped_reason or "planned")
        lines.append(
            f"  {action.issue_identifier or action.idempotency_key}: "
            f"{action.previous_status or 'unknown'} -> {action.new_status or 'n/a'} "
            f"[{action.pr_state}] ({marker})"
        )
    sys.stdout.write("\n".join(lines) + "\n")
    return 0


def main(argv: Sequence[str] | None = None) -> int:
    argv_list = list(argv) if argv is not None else sys.argv[1:]
    args = _build_parser().parse_args(argv_list)

    if args.command == "reconcile-outcomes":
        cfg = RunConfig(
            repo=args.repo,
            dry_run=args.dry_run,
            limit=args.limit,
            output_dir=args.output_dir,
            extra={"command_args": argv_list},
        )
        try:
            return _reconcile_outcomes_command(cfg)
        except LinearMemoryUnavailable as err:
            sys.stderr.write(f"feedback_loop: Linear memory unavailable: {err}\n")
            return 3

    cfg = RunConfig(
        repo=args.repo,
        dry_run=args.dry_run,
        since=args.since,
        until=args.until,
        limit=args.limit,
        pr_url=args.pr_url,
        output_dir=args.output_dir or _default_execute_output_dir(args),
        repo_root=args.repo_root or _detect_repo_root(),
        extra={
            "command_args": argv_list,
        },
    )

    try:
        if args.pr_url:
            return _run(cfg, [args.pr_url], "pr")
        # --backfill: enumerate merged PRs and cluster the whole bounded window together.
        pr_urls = harvest.list_merged_prs(cfg)
        return _run(cfg, pr_urls, "backfill")
    except harvest.HarvestError as err:
        sys.stderr.write(f"feedback_loop: harvest failed: {err}\n")
        return 2
    except NotImplementedError as err:
        sys.stderr.write(f"feedback_loop: stage not implemented yet: {err}\n")
        return 3
    except ProposalEvalBlocked as err:
        sys.stderr.write(f"feedback_loop: proposal eval blocked: {err}\n")
        return 3
    except LinearMemoryUnavailable as err:
        sys.stderr.write(f"feedback_loop: Linear memory unavailable: {err}\n")
        return 3
