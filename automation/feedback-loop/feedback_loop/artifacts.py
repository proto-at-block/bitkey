"""Local dry-run artifact bundle writer."""

from __future__ import annotations

from dataclasses import dataclass
import json
from pathlib import Path
import re
import shutil
from typing import Any

from .config import RunConfig
from .models import Proposal
from .pipeline.emit import EmitResult
from .pipeline.triage import TriageReport
from .redaction import redact_text, redact_value
from .route_metadata import proposal_change_set_id
from .util import dedupe as _dedupe, resolution_counts


@dataclass(frozen=True)
class BlockedProposal:
    """Proposal that was generated but did not reach PR-ready state."""

    proposal: Proposal
    reason: str


def write_run_bundle(
    cfg: RunConfig,
    *,
    mode: str,
    pr_urls: list[str],
    counts: dict[str, int],
    proposal_eval: dict[str, Any] | None,
    proposal_evals: list[dict[str, Any]] | None,
    llm_learnings: list[dict[str, Any]] | None,
    llm_debug: dict[str, Any] | None,
    triage_report: TriageReport,
    full_triage_report: TriageReport,
    proposals: list[Proposal],
    blocked_proposals: list[BlockedProposal],
    emit_results: list[EmitResult],
    linear_memory: dict[str, Any] | None = None,
    linear_memory_summary: dict[str, Any] | None = None,
    execution: dict[str, Any] | None = None,
    replay_gate: list[dict[str, Any]] | None = None,
    suggested_replay_cases: list[dict[str, Any]] | None = None,
    classifications: list[dict[str, Any]] | None = None,
    clusters_artifact: list[dict[str, Any]] | None = None,
) -> Path | None:
    """Write reviewable local artifacts for one run (dry-run preview or execute record)."""
    if not cfg.output_dir:
        return None

    output_dir = Path(cfg.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    _write_json(
        output_dir / "run-summary.json",
        _run_summary(
            cfg,
            mode=mode,
            pr_urls=pr_urls,
            counts=counts,
            proposal_count=len(proposals),
            ready_count=counts.get("pr_ready_proposals", len(emit_results)),
            blocked_count=len(blocked_proposals),
            proposal_eval=proposal_eval,
            comment_volume_summary=triage_report.comment_volume_summary,
            linear_memory=linear_memory_summary,
            execution=execution,
        ),
    )
    _write_text(output_dir / "triage-report.md", triage_report.markdown)
    _write_text(output_dir / "triage-report-full.md", full_triage_report.markdown)
    _write_json(output_dir / "triage-summary.json", full_triage_report.summary)
    _write_json(output_dir / "proposals.json", [_proposal_json(proposal) for proposal in proposals])
    _write_json(output_dir / "proposal-evals.json", proposal_evals or [])
    _write_json(output_dir / "llm-learnings.json", llm_learnings or [])
    _write_json(output_dir / "llm-debug.json", llm_debug or {})
    _write_json(
        output_dir / "eval-blocked.json",
        [_blocked_proposal_json(item) for item in blocked_proposals],
    )
    _write_json(output_dir / "cluster-memory.json", linear_memory or {})
    _write_json(output_dir / "classifications.json", classifications or [])
    _write_json(output_dir / "clusters.json", clusters_artifact or [])
    _write_json(output_dir / "replay-gate.json", replay_gate or [])
    _write_json(output_dir / "suggested-replay-cases.json", suggested_replay_cases or [])
    if execution is not None:
        _write_json(output_dir / "linear-writes.json", execution)
    _write_emit_preview(output_dir / "emit-preview", emit_results)
    return output_dir


def _run_summary(
    cfg: RunConfig,
    *,
    mode: str,
    pr_urls: list[str],
    counts: dict[str, int],
    proposal_count: int,
    ready_count: int,
    blocked_count: int,
    proposal_eval: dict[str, Any] | None,
    comment_volume_summary: dict[str, int],
    linear_memory: dict[str, Any] | None,
    execution: dict[str, Any] | None,
) -> dict[str, Any]:
    return {
        "mode": mode,
        "repo": cfg.repo,
        "dry_run": cfg.dry_run,
        "pr_urls": pr_urls,
        "since": cfg.since,
        "until": cfg.until,
        "limit": cfg.limit,
        "counts_by_stage": counts,
        "comment_volume_summary": comment_volume_summary,
        "proposal_readiness": {
            "total": proposal_count,
            "pr_ready": ready_count,
            "blocked": blocked_count,
        },
        "proposal_eval": proposal_eval
        or {
            "evaluator": "llm",
            "learning_count": 0,
            "proposal_count": 0,
            "eval_count": 0,
            "route_counts": {},
            "confidence_bands": {"high": 0, "medium": 0, "low": 0},
            "publishable": 0,
            "blocked": 0,
            "pr_ready": 0,
            "errors": 0,
        },
        "linear_memory": linear_memory or {},
        "execution": execution or {},
        "command_args": list(cfg.extra.get("command_args", [])),
    }


def _proposal_json(proposal: Proposal) -> dict[str, Any]:
    artifact = proposal.eval_artifact
    return {
        "cluster_slug": proposal.cluster.slug,
        "summary": proposal.summary,
        "destination": proposal.destination,
        "learning_id": proposal.learning_id,
        "route_id": proposal.route_id,
        "route_role": proposal.route_role,
        "handoff_title": proposal.handoff_title,
        "change_set_id": proposal_change_set_id(proposal),
        "linked_route_destinations": list(proposal.linked_route_destinations),
        "confidence": proposal.confidence,
        "eval_state": proposal.eval_state,
        "eval_passed": proposal.eval_passed,
        "llm_rubric_scores": dict(proposal.llm_rubric_scores),
        "resolution_state": _cluster_resolution_state(proposal.cluster),
        "resolution_counts": _cluster_resolution_counts(proposal.cluster),
        "resolution_evidence_ids": _cluster_resolution_evidence_ids(proposal.cluster),
        "coverage_paths": _cluster_coverage_paths(proposal.cluster),
        "evidence_urls": list(proposal.evidence_urls),
        "target_artifacts": list(proposal.target_artifacts),
        "validation_commands": list(proposal.validation_commands),
        "acceptance_criteria": _section_lines(proposal.sections.get("acceptance_criteria", "")),
        "false_positive_controls": _section_lines(proposal.sections.get("false_positive_controls", "")),
        "replay_cases": list(proposal.replay_cases),
        "template_title": proposal.template_title,
        "dry_run_only": proposal.dry_run_only,
        "file_changes": [
            {
                "path": change.path,
                "mode": change.mode,
                "content_bytes": len(change.content.encode("utf-8")),
            }
            for change in proposal.file_changes
        ],
        "eval_artifact": None
        if artifact is None
        else {
            "state": artifact.state,
            "cluster_slug": artifact.cluster_slug,
            "matched_replay_case_ids": list(artifact.matched_replay_case_ids),
            "blocking_reasons": list(artifact.blocking_reasons),
            "failure_destination": artifact.failure_destination,
            "manual_override": artifact.manual_override,
            "future_pr_url": artifact.future_pr_url,
            "llm_rubric_scores": dict(artifact.llm_rubric_scores),
        },
    }


def _blocked_proposal_json(item: BlockedProposal) -> dict[str, Any]:
    proposal = item.proposal
    artifact = proposal.eval_artifact
    return {
        "cluster_slug": proposal.cluster.slug,
        "summary": proposal.summary,
        "destination": proposal.destination,
        "learning_id": proposal.learning_id,
        "route_id": proposal.route_id,
        "route_role": proposal.route_role,
        "handoff_title": proposal.handoff_title,
        "change_set_id": proposal_change_set_id(proposal),
        "linked_route_destinations": list(proposal.linked_route_destinations),
        "eval_state": proposal.eval_state,
        "blocking_reason": item.reason,
        "blocking_reasons": [] if artifact is None else list(artifact.blocking_reasons),
        "failure_destination": "none" if artifact is None else artifact.failure_destination,
        "matched_replay_case_ids": []
        if artifact is None
        else list(artifact.matched_replay_case_ids),
        "resolution_state": _cluster_resolution_state(proposal.cluster),
        "resolution_evidence_ids": _cluster_resolution_evidence_ids(proposal.cluster),
        "coverage_paths": _cluster_coverage_paths(proposal.cluster),
        "evidence_urls": list(proposal.evidence_urls),
        "target_artifacts": list(proposal.target_artifacts),
        "validation_commands": list(proposal.validation_commands),
        "llm_rubric_scores": dict(proposal.llm_rubric_scores),
    }


def _write_emit_preview(preview_dir: Path, emit_results: list[EmitResult]) -> None:
    if preview_dir.exists():
        shutil.rmtree(preview_dir)
    preview_dir.mkdir(parents=True, exist_ok=True)

    for index, result in enumerate(emit_results, start=1):
        slug = _preview_slug(result.proposal)
        prefix = f"{index:03d}-{slug}"
        _write_json(
            preview_dir / f"{prefix}-metadata.json",
            {
                "cluster_slug": result.proposal.cluster.slug,
                "destination": result.proposal.destination,
                "learning_id": result.proposal.learning_id,
                "route_id": result.proposal.route_id,
                "route_role": result.proposal.route_role,
                "handoff_title": result.proposal.handoff_title,
                "change_set_id": proposal_change_set_id(result.proposal),
                "draft_pr_title": result.draft_pr.title,
                "linear_issue_title": result.cluster_issue.title,
                "linear_issue_labels": list(result.cluster_issue.labels),
                "target_artifacts": list(result.draft_pr.target_artifacts),
                "evidence_urls": list(result.draft_pr.evidence_urls),
                "validation_commands": list(result.draft_pr.validation_commands),
                "linear_issue_url": result.linear_issue_url,
            },
        )
        _write_text(preview_dir / f"{prefix}-draft-pr.md", result.draft_pr.body)
        _write_text(preview_dir / f"{prefix}-linear-issue.md", result.cluster_issue.description)


def _section_lines(content: str) -> list[str]:
    return [line.strip() for line in content.splitlines() if line.strip()]


def _cluster_resolution_state(cluster: object) -> str:
    signals = list(getattr(cluster, "learning_signals", []))
    if not signals:
        return "unresolved"
    states = {_signal_resolution_state(signal) for signal in signals}
    if len(states) == 1:
        return next(iter(states))
    return "mixed"


def _cluster_resolution_counts(cluster: object) -> dict[str, int]:
    return resolution_counts(getattr(cluster, "signals", []))


def _cluster_resolution_evidence_ids(cluster: object) -> list[str]:
    values: list[str] = []
    for signal in getattr(cluster, "signals", []):
        resolution = getattr(signal, "resolution", None)
        if resolution is not None:
            values.extend(resolution.evidence_signal_ids)
    return _dedupe(values)


def _cluster_coverage_paths(cluster: object) -> list[str]:
    values: list[str] = []
    for signal in getattr(cluster, "signals", []):
        resolution = getattr(signal, "resolution", None)
        if resolution is not None:
            values.extend(resolution.coverage_paths)
    return _dedupe(values)


def _signal_resolution_state(signal: object) -> str:
    resolution = getattr(signal, "resolution", None)
    if resolution is None:
        return "unresolved"
    return str(resolution.state)


def _write_json(path: Path, payload: Any) -> None:
    path.write_text(
        json.dumps(redact_value(payload), indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )


def _write_text(path: Path, content: str) -> None:
    suffix = "" if content.endswith("\n") else "\n"
    path.write_text(redact_text(content) + suffix, encoding="utf-8")


def _slug(value: str) -> str:
    slug = re.sub(r"[^a-z0-9]+", "-", value.casefold()).strip("-")
    return slug[:80] or "proposal"


def _preview_slug(proposal: Proposal) -> str:
    parts = [
        proposal.route_id,
        proposal.learning_id,
        proposal.destination,
    ]
    return _slug("-".join(part for part in parts if part))
