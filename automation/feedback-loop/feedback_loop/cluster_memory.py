"""Durable Linear-backed memory for feedback-loop clusters (schema v2).

GitHub remains the source of raw evidence. Linear cluster issues store only durable theme
summaries, metadata, and source links. Identity is the semantic cluster slug (minted/matched by
the LLM clusterer and validated deterministically) plus destination — never lexical body tokens
and never LLM-unstable learning ids. Legacy v1/lexical records keep parsing and upgrade in place
under their original idempotency keys the first time a cluster matches them.
"""

from __future__ import annotations

from dataclasses import dataclass, replace
from datetime import datetime, timezone
import hashlib
import json
import os
import re
import subprocess
from typing import Any, Literal, Protocol

from .linear_control import (
    BUILDERBOT_APPROVAL_LABEL,
    ClusterIssuePlan,
    ClusterIssueStatus,
    _builderbot_context_lines,
    _builderbot_lines,
    _eval_markdown,
    _item_lines,
    linear_state_for_cluster_status,
)
from .models import Cluster, Destination, NormalizedSignal, Proposal
from .pipeline.emit import EmitResult
from .route_metadata import proposal_change_set_id
from .util import (
    GITHUB_PR_URL_RE,
    RESOLUTION_STATES,
    dedupe as _dedupe,
    env_int,
    pr_numbers_from_urls,
    promotion_threshold,
    resolution_counts as _signal_resolution_counts,
)

DEFAULT_LINEAR_TEAM_KEY = "BKW"
DEFAULT_LINEAR_PROJECT_NAME = "Bitkey Feedback Loop"
MEMORY_SCHEMA_VERSION = 2
MEMORY_BLOCK_START = "<!-- feedback-loop:cluster-memory:v1"
MEMORY_BLOCK_END = "-->"
LEGACY_CLUSTER_RE = re.compile(r"Cluster:\s*`([^`]+)`")
LEGACY_DESTINATION_RE = re.compile(r"Destination:\s*`([^`]+)`")
MEMORY_BLOCK_RE = re.compile(
    rf"{re.escape(MEMORY_BLOCK_START)}\s*(.*?)\s*{re.escape(MEMORY_BLOCK_END)}",
    re.DOTALL,
)

# Only durable, accruing decisions earn a Linear row; already-covered/review-only/ignored
# clusters stay in run artifacts. gather_more_evidence must persist so sub-threshold recurring
# themes can accumulate frequency across runs.
MEMORY_WRITE_DECISIONS = frozenset(
    {"promote", "convert_to_mechanical_check", "gather_more_evidence"}
)
# Upper bound on Linear writes per run. Default raised from the original 30 because that cap
# silently dropped sub-threshold `gather_more_evidence` themes that must persist to accumulate
# frequency across runs — when over the cap we now keep the themes *closest to promoting* first
# (see `_capped_upserts`). Tunable via env for very large corpora.
MAX_MEMORY_UPSERTS_PER_RUN = 200
MEMORY_UPSERT_CAP_ENV = "FEEDBACK_LOOP_MEMORY_UPSERT_CAP"
# Read pagination cap (pages of `search-issues`, ~100 issues/page/state). Tunable so a large
# feedback-loop project does not silently orphan old themes from re-matching.
MAX_MEMORY_READ_PAGES = 10
MEMORY_READ_PAGES_ENV = "FEEDBACK_LOOP_MEMORY_READ_PAGES"
ReadStatus = Literal["ok", "skipped", "unavailable"]
WriteStatus = Literal["dry_run_preview", "written", "skipped"]
DECISION_PRIORITY = {"promote": 0, "convert_to_mechanical_check": 1, "gather_more_evidence": 2}


def _memory_upsert_cap() -> int:
    return env_int(MEMORY_UPSERT_CAP_ENV, MAX_MEMORY_UPSERTS_PER_RUN)


def _memory_read_pages() -> int:
    return env_int(MEMORY_READ_PAGES_ENV, MAX_MEMORY_READ_PAGES)


def _distance_to_promotion(severity: str, frequency: int) -> int:
    """PRs still needed before this theme could promote (negative once it's over threshold).

    Used to prioritize which themes survive the upsert cap: a theme one PR short of promoting must
    outrank a brand-new singleton, because dropping it resets the recurrence we are trying to
    accumulate.
    """
    return promotion_threshold(severity) - frequency


class LinearMemoryUnavailable(RuntimeError):
    """Linear memory could not be read or written."""


class ClusterMemoryReader(Protocol):
    """Reads existing feedback-loop cluster memory from Linear."""

    def read_cluster_memory(self) -> "ClusterMemoryReadResult":
        """Return parsed Linear memory records."""


class ClusterMemoryWriter(Protocol):
    """Writes feedback-loop cluster memory to Linear."""

    def upsert_cluster_memory(
        self,
        plan: ClusterIssuePlan,
        existing: "ClusterMemoryRecord | None" = None,
    ) -> str:
        """Create or update one Linear issue and return its URL."""


@dataclass(frozen=True)
class LinearIssueSnapshot:
    """Minimal Linear issue fields needed to parse cluster memory."""

    identifier: str
    url: str
    title: str
    description: str
    labels: tuple[str, ...] = ()
    state: str = ""


@dataclass(frozen=True)
class ClusterMemoryMetadata:
    """Machine-readable metadata hidden in the Linear issue body."""

    schema_version: int
    idempotency_key: str
    memory_slug: str
    destination: str | None
    decision: str
    source_urls: tuple[str, ...]
    distinct_pr_numbers: tuple[int, ...]
    resolution_counts: dict[str, int]
    coverage_paths: tuple[str, ...]
    last_seen_at: str
    window: dict[str, Any]
    eval_state: str = ""
    issue_status: str = ""
    change_set_id: str = ""
    outcome_pr_url: str = ""
    outcome_checked_at: str = ""


@dataclass(frozen=True)
class ClusterMemoryRecord:
    """Existing Linear issue memory parsed by idempotency key."""

    issue_identifier: str
    issue_url: str
    title: str
    metadata: ClusterMemoryMetadata
    from_metadata: bool
    description: str = ""
    warnings: tuple[str, ...] = ()

    @property
    def idempotency_key(self) -> str:
        return self.metadata.idempotency_key

    @property
    def memory_slug(self) -> str:
        return self.metadata.memory_slug

    @property
    def source_urls(self) -> tuple[str, ...]:
        return self.metadata.source_urls

    @property
    def distinct_pr_numbers(self) -> tuple[int, ...]:
        return self.metadata.distinct_pr_numbers


@dataclass(frozen=True)
class ClusterMemoryReadResult:
    """Result of reading Linear cluster memory."""

    status: ReadStatus
    records: tuple[ClusterMemoryRecord, ...] = ()
    warnings: tuple[str, ...] = ()
    error: str = ""


@dataclass(frozen=True)
class ClusterMemoryReconciliation:
    """How one current cluster merged with Linear memory history."""

    idempotency_key: str
    cluster_slug: str
    matched_issue_identifier: str = ""
    matched_issue_url: str = ""
    frequency_before: int = 0
    frequency_after: int = 0
    current_pr_numbers: tuple[int, ...] = ()
    historical_pr_numbers: tuple[int, ...] = ()
    merged_pr_numbers: tuple[int, ...] = ()
    evidence_urls_added: tuple[str, ...] = ()
    current_resolution_counts: dict[str, int] | None = None


@dataclass(frozen=True)
class PlannedClusterMemoryUpsert:
    """One planned Linear create/update for cluster memory."""

    plan: ClusterIssuePlan
    action: Literal["create", "update"]
    cluster_slug: str
    decision: str
    rank: float = 0.0
    severity: str = "low"
    frequency: int = 0
    existing_issue_identifier: str = ""
    existing_issue_url: str = ""
    proposal_eval_state: str = ""
    trigger_builderbot: bool = False


@dataclass(frozen=True)
class ClusterMemoryWriteResult:
    """Result of writing one planned Linear upsert."""

    idempotency_key: str
    issue_url: str
    action: str


@dataclass(frozen=True)
class ClusterMemoryPlanResult:
    """Planned/written cluster memory state for artifacts."""

    read_result: ClusterMemoryReadResult
    reconciliations: tuple[ClusterMemoryReconciliation, ...]
    upserts: tuple[PlannedClusterMemoryUpsert, ...]
    write_status: WriteStatus
    write_results: tuple[ClusterMemoryWriteResult, ...] = ()
    dropped_upserts: tuple[dict[str, Any], ...] = ()
    warnings: tuple[str, ...] = ()


def idempotency_key_for_memory(slug: str, destination: str | None) -> str:
    """Stable Linear memory key for one semantic cluster slug and destination.

    Destination stays part of the key on purpose: one learning can fan out to two routes (e.g. a
    test_or_linter check AND an agents_check) that each deserve their own durable Linear row.
    Destination *drift* for a single theme across runs (which would otherwise orphan a duplicate)
    is handled instead by (1) the LLM matching the theme to its record and (2) exact slug or
    evidence-URL folds in `_fold_into_existing`. So identity supports multi-route while drift still
    converges without folding unrelated themes that merely appeared on the same PR.
    """
    raw_key = f"slug:{slug}|dest:{destination or 'manual_triage'}"
    digest = hashlib.sha256(raw_key.encode("utf-8")).hexdigest()[:16]
    return f"feedback-loop:{digest}"


def memory_key_for_cluster(cluster: Cluster, proposal: Proposal | None = None) -> str:
    """Return the route-specific memory key, preserving exact matched keys when unambiguous."""
    destination = proposal.destination if proposal is not None else cluster.suggested_destination
    derived = idempotency_key_for_memory(cluster.slug, destination)
    if cluster.matched_memory_key:
        if (
            proposal is None
            or cluster.matched_memory_key == derived
            or cluster.suggested_destination == proposal.destination
        ):
            return cluster.matched_memory_key
    return derived


def should_write_cluster_memory(cluster: Cluster) -> bool:
    """Only durable decisions get Linear rows; everything else stays in run artifacts."""
    return cluster.decision in MEMORY_WRITE_DECISIONS


def render_memory_metadata_block(metadata: ClusterMemoryMetadata) -> str:
    """Render the hidden JSON metadata block stored in Linear issue bodies."""
    payload = {
        "schema_version": metadata.schema_version,
        "idempotency_key": metadata.idempotency_key,
        "memory_slug": metadata.memory_slug,
        "destination": metadata.destination,
        "decision": metadata.decision,
        "source_urls": list(metadata.source_urls),
        "distinct_pr_numbers": list(metadata.distinct_pr_numbers),
        "resolution_counts": dict(metadata.resolution_counts),
        "coverage_paths": list(metadata.coverage_paths),
        "last_seen_at": metadata.last_seen_at,
        "window": metadata.window,
        "eval_state": metadata.eval_state,
        "issue_status": metadata.issue_status,
        "change_set_id": metadata.change_set_id,
        "outcome_pr_url": metadata.outcome_pr_url,
        "outcome_checked_at": metadata.outcome_checked_at,
    }
    return (
        f"{MEMORY_BLOCK_START}\n"
        f"{json.dumps(payload, indent=2, sort_keys=True)}\n"
        f"{MEMORY_BLOCK_END}"
    )


def parse_memory_metadata_block(
    description: str,
) -> tuple[ClusterMemoryMetadata | None, tuple[str, ...]]:
    """Parse the hidden metadata block, ignoring malformed blocks with warnings."""
    match = MEMORY_BLOCK_RE.search(description)
    if match is None:
        return None, ()

    try:
        payload = json.loads(match.group(1))
        metadata = _metadata_from_payload(payload)
    except (TypeError, ValueError, json.JSONDecodeError) as err:
        return None, (f"ignored malformed feedback-loop cluster metadata: {err}",)
    return metadata, ()


def replace_memory_metadata_block(description: str, metadata: ClusterMemoryMetadata) -> str:
    """Substitute the hidden metadata block in an existing issue description."""
    rendered = render_memory_metadata_block(metadata)
    if MEMORY_BLOCK_RE.search(description):
        return MEMORY_BLOCK_RE.sub(lambda _match: rendered, description, count=1)
    return f"{description.rstrip()}\n\n{rendered}\n"


def parse_linear_issue_memory(issue: LinearIssueSnapshot) -> ClusterMemoryRecord | None:
    """Parse current or legacy Linear issue descriptions into memory records."""
    metadata, warnings = parse_memory_metadata_block(issue.description)
    if metadata is not None:
        return ClusterMemoryRecord(
            issue_identifier=issue.identifier,
            issue_url=issue.url,
            title=issue.title,
            metadata=metadata,
            from_metadata=True,
            description=issue.description,
            warnings=warnings,
        )

    fallback = _legacy_metadata(issue.description)
    if fallback is None:
        return None
    return ClusterMemoryRecord(
        issue_identifier=issue.identifier,
        issue_url=issue.url,
        title=issue.title,
        metadata=fallback,
        from_metadata=False,
        description=issue.description,
        warnings=warnings,
    )


def read_linear_memory(
    *,
    dry_run: bool,
    reader: ClusterMemoryReader | None,
) -> ClusterMemoryReadResult:
    """Read memory, allowing dry-runs to continue when Linear is unavailable."""
    if reader is None:
        if not dry_run:
            raise LinearMemoryUnavailable("Linear memory reader is not configured.")
        return ClusterMemoryReadResult(
            status="skipped",
            warnings=(
                "Linear memory read skipped; no reader configured. Cluster frequency reflects "
                "THIS run only, so a cold dry-run shows frequency=1 for non-recurring themes — "
                "expected, not a failure. Set FEEDBACK_LOOP_LINEAR_READ=1 (or use --execute) to "
                "accumulate frequency across runs.",
            ),
        )
    try:
        return reader.read_cluster_memory()
    except LinearMemoryUnavailable:
        if not dry_run:
            raise
        return ClusterMemoryReadResult(
            status="unavailable",
            error="Linear memory unavailable.",
            warnings=("Linear memory unavailable; dry-run continued without historical memory.",),
        )
    except Exception as err:
        if not dry_run:
            raise LinearMemoryUnavailable(str(err)) from err
        return ClusterMemoryReadResult(
            status="unavailable",
            error=str(err),
            warnings=(
                f"Linear memory unavailable; dry-run continued without historical memory: {err}",
            ),
        )


def build_cluster_memory_issue_plan(
    cluster: Cluster,
    *,
    proposal: Proposal | None = None,
    pr_url: str = "",
    decision: str = "",
    status: ClusterIssueStatus = "needs_triage",
    team_key: str = DEFAULT_LINEAR_TEAM_KEY,
    project_name: str = DEFAULT_LINEAR_PROJECT_NAME,
    trigger_builderbot: bool = False,
    validation_commands: tuple[str, ...] = (),
    draft_pr_title: str = "",
    draft_pr_body: str = "",
    distinct_pr_numbers: tuple[int, ...] | None = None,
    window: dict[str, Any] | None = None,
    now: datetime | None = None,
    suggested_replay_case: dict[str, Any] | None = None,
) -> ClusterIssuePlan:
    """Build a Linear issue upsert plan from a cluster, optionally with proposal context."""
    destination = proposal.destination if proposal is not None else cluster.suggested_destination
    idempotency_key = memory_key_for_cluster(cluster, proposal)
    resolved_decision = decision or cluster.decision or _decision_for_cluster(cluster, proposal)
    metadata = ClusterMemoryMetadata(
        schema_version=MEMORY_SCHEMA_VERSION,
        idempotency_key=idempotency_key,
        memory_slug=cluster.slug,
        destination=destination,
        decision=resolved_decision,
        source_urls=tuple(cluster.source_urls),
        distinct_pr_numbers=distinct_pr_numbers
        if distinct_pr_numbers is not None
        else (
            cluster.merged_pr_numbers
            or _current_pr_numbers(cluster, fallback_urls=cluster.source_urls)
        ),
        resolution_counts=_resolution_counts(cluster.signals),
        coverage_paths=_coverage_paths(cluster.signals),
        last_seen_at=_timestamp(now),
        window=window or {},
        eval_state="" if proposal is None else proposal.eval_state,
        issue_status=status,
        change_set_id="" if proposal is None else proposal_change_set_id(proposal),
    )
    return ClusterIssuePlan(
        idempotency_key=idempotency_key,
        title=_issue_title(cluster, proposal),
        description=_issue_description(
            cluster,
            metadata,
            proposal=proposal,
            pr_url=pr_url,
            trigger_builderbot=trigger_builderbot,
            validation_commands=validation_commands,
            draft_pr_title=draft_pr_title,
            draft_pr_body=draft_pr_body,
            suggested_replay_case=suggested_replay_case,
        ),
        team_key=team_key,
        project_name=project_name,
        status=status,
        linear_state=linear_state_for_cluster_status(status),
        assignee=None,
        labels=_issue_labels(destination, trigger_builderbot),
    )


def plan_cluster_memory_upserts(
    clusters: list[Cluster],
    *,
    proposals: list[Proposal],
    emit_results: list[EmitResult],
    existing_records: tuple[ClusterMemoryRecord, ...],
    dry_run: bool,
    reconciliations: tuple[ClusterMemoryReconciliation, ...] = (),
    window: dict[str, Any] | None = None,
) -> ClusterMemoryPlanResult:
    """Plan one Linear upsert per durable cluster; proposals attach to their cluster's row."""
    proposals_by_slug: dict[str, dict[str, Proposal]] = {}
    for proposal in proposals:
        slug = proposal.cluster.slug
        key = memory_key_for_cluster(proposal.cluster, proposal)
        proposals_for_slug = proposals_by_slug.setdefault(slug, {})
        current = proposals_for_slug.get(key)
        if current is None or _proposal_priority(proposal) < _proposal_priority(current):
            proposals_for_slug[key] = proposal

    emit_by_key = {result.cluster_issue.idempotency_key: result for result in emit_results}
    records_by_key = {record.idempotency_key: record for record in existing_records}
    warnings: list[str] = []

    upserts: list[PlannedClusterMemoryUpsert] = []
    planned_keys: set[str] = set()
    cluster_slugs = {cluster.slug for cluster in clusters}

    def plan_one(cluster: Cluster, proposal: Proposal | None) -> None:
        key = memory_key_for_cluster(cluster, proposal)
        existing = records_by_key.get(key)
        if existing is None:
            folded = _fold_into_existing(cluster, proposal, existing_records)
            if folded is not None:
                warnings.append(
                    f"semantic create for `{cluster.slug}` folded into existing issue "
                    f"{folded.issue_identifier or folded.idempotency_key}"
                )
                cluster = replace(cluster, matched_memory_key=folded.idempotency_key)
                key = folded.idempotency_key
                existing = folded
        if key in planned_keys:
            return
        planned_keys.add(key)
        upserts.append(
            _planned_upsert_for_cluster(
                cluster,
                proposal=proposal,
                emit_result=emit_by_key.get(key),
                existing=existing,
                window=window,
            )
        )

    for cluster in clusters:
        if not should_write_cluster_memory(cluster):
            continue
        proposals_for_cluster = list(proposals_by_slug.get(cluster.slug, {}).values())
        if proposals_for_cluster:
            for proposal in proposals_for_cluster:
                plan_one(cluster, proposal)
        else:
            plan_one(cluster, None)

    # Proposals whose synthetic clusters never appeared in the run's cluster list (e.g. a
    # learning matched no cluster) still need a row, or the pr_ready handoff silently vanishes.
    for slug, proposals_for_slug in proposals_by_slug.items():
        if slug in cluster_slugs:
            continue
        for proposal in proposals_for_slug.values():
            synthetic = proposal.cluster
            if not synthetic.decision:
                synthetic = replace(synthetic, decision="promote")
            if should_write_cluster_memory(synthetic):
                plan_one(synthetic, proposal)

    upserts, dropped = _capped_upserts(upserts, warnings)
    return ClusterMemoryPlanResult(
        read_result=ClusterMemoryReadResult(status="skipped"),
        reconciliations=reconciliations,
        upserts=tuple(upserts),
        write_status="dry_run_preview" if dry_run else "skipped",
        dropped_upserts=tuple(dropped),
        warnings=tuple(warnings),
    )


def _fold_into_existing(
    cluster: Cluster,
    proposal: Proposal | None,
    existing_records: tuple[ClusterMemoryRecord, ...],
) -> ClusterMemoryRecord | None:
    destination = proposal.destination if proposal is not None else cluster.suggested_destination
    source_urls = set(cluster.source_urls)
    source_urls.update(signal.source_url for signal in cluster.signals if signal.source_url)
    if not cluster.slug and not source_urls:
        return None
    # Destination drift can still converge, but only when the current theme has a strong identity
    # match: exact cluster slug or exact evidence URL overlap. PR-number overlap alone is too broad
    # because unrelated review themes often occur on the same PR.
    route_specific_pr_ready = proposal is not None and proposal.eval_state == "pr_ready"
    candidates: list[tuple[ClusterMemoryRecord, bool, int]] = []
    for record in existing_records:
        same_slug = bool(cluster.slug and record.memory_slug == cluster.slug)
        overlap_count = len(source_urls & set(record.source_urls))
        if not same_slug and overlap_count == 0:
            continue
        if route_specific_pr_ready and record.metadata.destination != destination:
            continue
        candidates.append((record, same_slug, overlap_count))
    if not candidates:
        return None
    candidates.sort(
        key=lambda item: (
            item[0].metadata.destination != destination,
            not item[1],
            -item[2],
            item[0].issue_identifier,
        )
    )
    return candidates[0][0]


def _proposal_priority(proposal: Proposal) -> tuple[int, str]:
    return (
        0 if proposal.eval_state == "pr_ready" else 1,
        proposal.route_id or proposal.destination,
    )


def _capped_upserts(
    upserts: list[PlannedClusterMemoryUpsert],
    warnings: list[str],
) -> tuple[list[PlannedClusterMemoryUpsert], list[dict[str, Any]]]:
    cap = _memory_upsert_cap()
    if len(upserts) <= cap:
        return upserts, []
    # Keep order: promote/convert first, then the themes closest to their promotion threshold
    # (smallest distance-to-promotion wins), then higher rank, then slug for determinism. This
    # protects near-threshold recurring themes from being silently dropped — exactly the
    # cross-run accumulation the cap previously starved.
    ordered = sorted(
        upserts,
        key=lambda item: (
            DECISION_PRIORITY.get(item.decision, 9),
            _distance_to_promotion(item.severity, item.frequency),
            -item.rank,
            item.cluster_slug,
        ),
    )
    kept = ordered[:cap]
    dropped = ordered[cap:]
    dropped_payload = [
        {
            "cluster_slug": item.cluster_slug,
            "decision": item.decision,
            "rank": item.rank,
            "idempotency_key": item.plan.idempotency_key,
        }
        for item in dropped
    ]
    for item in dropped_payload:
        warnings.append(
            f"memory upsert cap dropped `{item['cluster_slug']}` "
            f"(decision={item['decision']}, rank={item['rank']})"
        )
    return kept, dropped_payload


def _planned_upsert_for_cluster(
    cluster: Cluster,
    *,
    proposal: Proposal | None,
    emit_result: EmitResult | None,
    existing: ClusterMemoryRecord | None,
    window: dict[str, Any] | None,
) -> PlannedClusterMemoryUpsert:
    decision = cluster.decision or _decision_for_cluster(cluster, proposal)
    if emit_result is not None:
        plan = emit_result.cluster_issue
        trigger_builderbot = BUILDERBOT_APPROVAL_LABEL in plan.labels
    else:
        plan = build_cluster_memory_issue_plan(
            cluster,
            proposal=proposal,
            decision=decision,
            status=_status_for_cluster(cluster, proposal),
            trigger_builderbot=False,
            window=window,
        )
        trigger_builderbot = False

    return PlannedClusterMemoryUpsert(
        plan=plan,
        action="update" if existing is not None else "create",
        cluster_slug=cluster.slug,
        decision=decision,
        rank=cluster.rank,
        severity=cluster.severity or "low",
        frequency=cluster.frequency,
        existing_issue_identifier="" if existing is None else existing.issue_identifier,
        existing_issue_url="" if existing is None else existing.issue_url,
        proposal_eval_state="" if proposal is None else proposal.eval_state,
        trigger_builderbot=trigger_builderbot,
    )


def attach_memory_context(
    plan_result: ClusterMemoryPlanResult,
    *,
    read_result: ClusterMemoryReadResult,
    reconciliations: tuple[ClusterMemoryReconciliation, ...],
    write_status: WriteStatus | None = None,
    write_results: tuple[ClusterMemoryWriteResult, ...] = (),
    warnings: tuple[str, ...] = (),
) -> ClusterMemoryPlanResult:
    """Attach read/reconciliation/write context to a plan result."""
    return replace(
        plan_result,
        read_result=read_result,
        reconciliations=reconciliations,
        write_status=write_status or plan_result.write_status,
        write_results=write_results,
        warnings=tuple([*plan_result.warnings, *warnings]),
    )


def write_cluster_memory_upserts(
    plan_result: ClusterMemoryPlanResult,
    writer: ClusterMemoryWriter,
    existing_records: tuple[ClusterMemoryRecord, ...],
) -> ClusterMemoryPlanResult:
    """Write planned Linear upserts and return write results."""
    records_by_key = {record.idempotency_key: record for record in existing_records}
    results: list[ClusterMemoryWriteResult] = []
    for upsert in plan_result.upserts:
        issue_url = writer.upsert_cluster_memory(
            upsert.plan,
            existing=records_by_key.get(upsert.plan.idempotency_key),
        )
        results.append(
            ClusterMemoryWriteResult(
                idempotency_key=upsert.plan.idempotency_key,
                issue_url=issue_url,
                action=upsert.action,
            )
        )
    return replace(plan_result, write_status="written", write_results=tuple(results))


def cluster_memory_artifact(plan_result: ClusterMemoryPlanResult) -> dict[str, Any]:
    """Return the run-bundle JSON artifact payload for cluster memory."""
    matched_keys = {item.idempotency_key for item in plan_result.reconciliations}
    legacy_unmatched = sum(
        1
        for record in plan_result.read_result.records
        if (not record.from_metadata or record.metadata.schema_version < MEMORY_SCHEMA_VERSION)
        and record.idempotency_key not in matched_keys
    )
    return {
        "read": {
            "status": plan_result.read_result.status,
            "records": len(plan_result.read_result.records),
            "legacy_unmatched": legacy_unmatched,
            "warnings": list(plan_result.read_result.warnings),
            "error": plan_result.read_result.error,
        },
        "matched_existing_issues": [
            {
                "cluster_slug": item.cluster_slug,
                "idempotency_key": item.idempotency_key,
                "issue_identifier": item.matched_issue_identifier,
                "issue_url": item.matched_issue_url,
                "frequency_before": item.frequency_before,
                "frequency_after": item.frequency_after,
                "historical_pr_numbers": list(item.historical_pr_numbers),
                "current_pr_numbers": list(item.current_pr_numbers),
                "merged_pr_numbers": list(item.merged_pr_numbers),
                "evidence_urls_added": list(item.evidence_urls_added),
                "current_resolution_counts": item.current_resolution_counts or {},
            }
            for item in plan_result.reconciliations
            if item.matched_issue_identifier
        ],
        "reconciliations": [
            {
                "cluster_slug": item.cluster_slug,
                "idempotency_key": item.idempotency_key,
                "matched": bool(item.matched_issue_identifier),
                "frequency_before": item.frequency_before,
                "frequency_after": item.frequency_after,
                "evidence_urls_added": list(item.evidence_urls_added),
            }
            for item in plan_result.reconciliations
        ],
        "planned_upserts": [
            {
                "action": upsert.action,
                "idempotency_key": upsert.plan.idempotency_key,
                "cluster_slug": upsert.cluster_slug,
                "decision": upsert.decision,
                "change_set_id": _metadata_change_set_id(upsert.plan.description),
                "status": upsert.plan.status,
                "linear_state": upsert.plan.linear_state,
                "existing_issue_identifier": upsert.existing_issue_identifier,
                "existing_issue_url": upsert.existing_issue_url,
                "labels": list(upsert.plan.labels),
                "proposal_eval_state": upsert.proposal_eval_state,
                "trigger_builderbot": upsert.trigger_builderbot,
            }
            for upsert in plan_result.upserts
        ],
        "dropped_upserts": [dict(item) for item in plan_result.dropped_upserts],
        "write": {
            "eligible": bool(plan_result.upserts),
            "status": plan_result.write_status,
            "results": [
                {
                    "idempotency_key": result.idempotency_key,
                    "issue_url": result.issue_url,
                    "action": result.action,
                }
                for result in plan_result.write_results
            ],
            "warnings": list(plan_result.warnings),
        },
    }


def cluster_memory_summary(plan_result: ClusterMemoryPlanResult) -> dict[str, Any]:
    """Return the run-summary Linear memory section."""
    creates = sum(1 for item in plan_result.upserts if item.action == "create")
    updates = sum(1 for item in plan_result.upserts if item.action == "update")
    return {
        "read_status": plan_result.read_result.status,
        "existing_records": len(plan_result.read_result.records),
        "existing_issues_matched": sum(
            1 for item in plan_result.reconciliations if item.matched_issue_identifier
        ),
        "memory_upserts_planned": len(plan_result.upserts),
        "memory_upserts_dropped": len(plan_result.dropped_upserts),
        "creates": creates,
        "updates": updates,
        "write_status": plan_result.write_status,
        "warnings": [*plan_result.read_result.warnings, *plan_result.warnings],
        "error": plan_result.read_result.error,
    }


def _metadata_change_set_id(description: str) -> str:
    metadata, _warnings = parse_memory_metadata_block(description)
    return "" if metadata is None else metadata.change_set_id


class SqAgentToolsLinearClient:
    """Cluster memory reader/writer backed by `sq agent-tools linear`."""

    def __init__(
        self,
        *,
        command: tuple[str, ...] = ("sq", "agent-tools", "linear"),
        team_key: str = DEFAULT_LINEAR_TEAM_KEY,
        project_name: str = DEFAULT_LINEAR_PROJECT_NAME,
        limit: int = 100,
    ) -> None:
        self.command = command
        self.team_key = team_key
        self.project_name = project_name
        self.limit = limit

    def read_cluster_memory(self) -> ClusterMemoryReadResult:
        """Search active and completed feedback-loop Linear issues, paging past the limit."""
        warnings: list[str] = []
        try:
            issues = self._search_feedback_loop_issues(warnings)
            hydrated = [self._hydrate_issue(issue) for issue in issues]
        except (OSError, subprocess.CalledProcessError, ValueError) as err:
            raise LinearMemoryUnavailable(str(err)) from err

        records: list[ClusterMemoryRecord] = []
        seen_identifiers: set[str] = set()
        for issue in hydrated:
            if issue.identifier in seen_identifiers:
                continue
            seen_identifiers.add(issue.identifier)
            record = parse_linear_issue_memory(issue)
            if record is None:
                continue
            records.append(record)
            warnings.extend(record.warnings)
        return ClusterMemoryReadResult(
            status="ok",
            records=tuple(records),
            warnings=tuple(warnings),
        )

    def upsert_cluster_memory(
        self,
        plan: ClusterIssuePlan,
        existing: ClusterMemoryRecord | None = None,
    ) -> str:
        """Create or update a Linear issue for one memory plan."""
        payload: dict[str, Any] = {
            "title": plan.title,
            "description": plan.description,
            "state": plan.linear_state,
            "project": plan.project_name,
            "assignee": plan.assignee,
        }
        if existing is None:
            payload["team"] = plan.team_key
            payload["labels"] = list(plan.labels)
        else:
            payload["id"] = existing.issue_identifier
            payload["add_labels"] = list(plan.labels)
            if BUILDERBOT_APPROVAL_LABEL not in plan.labels:
                payload["remove_labels"] = [BUILDERBOT_APPROVAL_LABEL]

        try:
            result = self._run("save-issue", "--json", json.dumps(payload))
        except (OSError, subprocess.CalledProcessError, ValueError) as err:
            raise LinearMemoryUnavailable(str(err)) from err
        return _issue_url_from_payload(result)

    def update_issue_outcome(
        self,
        issue_identifier: str,
        *,
        state: str,
        description: str,
        remove_labels: tuple[str, ...] = (),
    ) -> str:
        """Update one issue's workflow state/description after outcome reconciliation."""
        payload: dict[str, Any] = {
            "id": issue_identifier,
            "state": state,
            "description": description,
        }
        if remove_labels:
            payload["remove_labels"] = list(remove_labels)
        try:
            result = self._run("save-issue", "--json", json.dumps(payload))
        except (OSError, subprocess.CalledProcessError, ValueError) as err:
            raise LinearMemoryUnavailable(str(err)) from err
        return _issue_url_from_payload(result)

    def _search_feedback_loop_issues(self, warnings: list[str]) -> list[dict[str, Any]]:
        issues: list[dict[str, Any]] = []
        state_groups = (
            ("triage", "backlog", "unstarted", "started"),
            ("completed",),
            ("canceled",),
        )
        for states in state_groups:
            issues.extend(self._search_pages(states, warnings))
        return issues

    def _search_pages(
        self,
        states: tuple[str, ...],
        warnings: list[str],
    ) -> list[dict[str, Any]]:
        issues: list[dict[str, Any]] = []
        cursor: str | None = None
        max_pages = _memory_read_pages()
        for _page in range(max_pages):
            args = [
                "search-issues",
                "--team-key",
                self.team_key,
                "--label",
                "feedback-loop",
                "--limit",
                str(self.limit),
                "--state-types",
                *states,
            ]
            if cursor:
                args.extend(["--cursor", cursor])
            payload = self._run(*args)
            page_items = _issues_from_payload(payload)
            issues.extend(page_items)
            cursor = _next_cursor(payload)
            if not cursor:
                if len(page_items) >= self.limit:
                    warnings.append(
                        "linear memory read may be truncated: page returned the full "
                        f"limit ({self.limit}) for states {','.join(states)} and the CLI "
                        "exposed no pagination cursor"
                    )
                break
        else:
            warnings.append(
                f"linear memory read stopped at the {max_pages}-page cap "
                f"for states {','.join(states)}; raise {MEMORY_READ_PAGES_ENV} if the "
                "feedback-loop project has more history"
            )
        return issues

    def _hydrate_issue(self, issue: dict[str, Any]) -> LinearIssueSnapshot:
        identifier = _issue_identifier(issue)
        if not identifier:
            raise ValueError(f"Linear issue is missing an identifier: {issue}")
        if issue.get("description"):
            return _snapshot_from_issue(issue)
        payload = self._run("get-issue", "--id", identifier)
        hydrated = _issue_from_payload(payload)
        return _snapshot_from_issue(hydrated)

    def _run(self, *args: str) -> dict[str, Any]:
        env = os.environ.copy()
        completed = subprocess.run(
            [*self.command, *args],
            check=True,
            capture_output=True,
            text=True,
            env=env,
        )
        return _json_from_stdout(completed.stdout)


def _metadata_from_payload(payload: dict[str, Any]) -> ClusterMemoryMetadata:
    schema_version = int(payload.get("schema_version", 0))
    if schema_version not in (1, MEMORY_SCHEMA_VERSION):
        raise ValueError(f"unsupported schema_version {payload.get('schema_version')}")
    idempotency_key = _required_str(payload, "idempotency_key")
    if schema_version == 1:
        memory_slug = _required_str(payload, "cluster_theme")
    else:
        memory_slug = _required_str(payload, "memory_slug")
    source_urls = tuple(_dedupe([str(value) for value in payload.get("source_urls", [])]))
    distinct_pr_numbers = tuple(
        sorted({int(value) for value in payload.get("distinct_pr_numbers", [])})
    )
    if not distinct_pr_numbers:
        distinct_pr_numbers = pr_numbers_from_urls(source_urls)
    return ClusterMemoryMetadata(
        schema_version=schema_version,
        idempotency_key=idempotency_key,
        memory_slug=memory_slug,
        destination=payload.get("destination"),
        decision=str(payload.get("decision", "")),
        source_urls=source_urls,
        distinct_pr_numbers=distinct_pr_numbers,
        resolution_counts=_coerce_resolution_counts(payload.get("resolution_counts", {})),
        coverage_paths=tuple(_dedupe([str(value) for value in payload.get("coverage_paths", [])])),
        last_seen_at=str(payload.get("last_seen_at", "")),
        window=dict(payload.get("window", {}) or {}),
        eval_state=str(payload.get("eval_state", "")),
        issue_status=str(payload.get("issue_status", "")),
        change_set_id=str(payload.get("change_set_id", "")),
        outcome_pr_url=str(payload.get("outcome_pr_url", "")),
        outcome_checked_at=str(payload.get("outcome_checked_at", "")),
    )


def _legacy_metadata(description: str) -> ClusterMemoryMetadata | None:
    cluster_match = LEGACY_CLUSTER_RE.search(description)
    destination_match = LEGACY_DESTINATION_RE.search(description)
    if cluster_match is None:
        return None
    route_key = destination_match.group(1) if destination_match is not None else "manual_triage"
    if route_key == "none":
        route_key = "manual_triage"
    source_urls = tuple(_dedupe(match.group(0) for match in GITHUB_PR_URL_RE.finditer(description)))
    theme = cluster_match.group(1)
    return ClusterMemoryMetadata(
        schema_version=1,
        idempotency_key=_legacy_idempotency_key(theme, route_key),
        memory_slug=theme,
        destination=None if route_key == "manual_triage" else route_key,
        decision="legacy",
        source_urls=source_urls,
        distinct_pr_numbers=pr_numbers_from_urls(source_urls),
        resolution_counts=_empty_resolution_counts(),
        coverage_paths=(),
        last_seen_at="",
        window={},
        eval_state="",
        issue_status="",
        change_set_id="",
    )


def _legacy_idempotency_key(theme: str, route_key: str) -> str:
    """v1 key derivation, kept so pre-metadata legacy issues update in place."""
    raw_key = "|".join([theme, route_key])
    digest = hashlib.sha256(raw_key.encode("utf-8")).hexdigest()[:16]
    return f"feedback-loop:{digest}"


def _issue_description(
    cluster: Cluster,
    metadata: ClusterMemoryMetadata,
    *,
    proposal: Proposal | None,
    pr_url: str,
    trigger_builderbot: bool,
    validation_commands: tuple[str, ...],
    draft_pr_title: str,
    draft_pr_body: str,
    suggested_replay_case: dict[str, Any] | None = None,
) -> str:
    source_urls = list(metadata.source_urls)
    coverage_paths = list(metadata.coverage_paths)
    coverage_ids = _resolution_evidence_ids(cluster.signals)
    commands = validation_commands or (() if proposal is None else tuple(proposal.validation_commands))
    lines = [
        "## Summary",
        _summary_text(cluster, proposal),
        "",
        "## Decision",
        f"- Decision: `{metadata.decision}`",
        f"- Linear status: `{metadata.issue_status}`",
        "",
        "## Routing",
        f"- Destination: `{metadata.destination or 'none'}`",
        f"- Cluster slug: `{metadata.memory_slug}`",
        f"- Change set: `{metadata.change_set_id or 'n/a'}`",
        "",
        "## Severity and frequency",
        f"- Severity: `{cluster.severity or 'unknown'}`",
        f"- Frequency: `{len(metadata.distinct_pr_numbers)}` distinct PR(s)",
        f"- Current-run resolution counts: `{json.dumps(metadata.resolution_counts, sort_keys=True)}`",
        "",
        "## Source links",
        *_item_lines("Evidence", source_urls),
        *_item_lines("Draft PR", [pr_url] if pr_url else []),
        "",
        "## Resolution coverage",
        *_item_lines("Resolution evidence IDs", coverage_ids),
        *_item_lines("Coverage paths", coverage_paths),
        "",
        "## Proposal/eval status",
        *_proposal_status_lines(proposal),
    ]
    if proposal is not None:
        lines.extend(
            [
                "",
                "## Target artifacts",
                *_item_lines("Target artifacts", proposal.target_artifacts),
                *_item_lines("Proposed file changes", [change.path for change in proposal.file_changes]),
            ]
        )
    lines.extend(
        [
            "",
            "## Builderbot handoff",
            *_builderbot_lines(trigger_builderbot),
            "",
            "## Builderbot implementation context",
            *_builderbot_context_lines_for_optional_proposal(
                proposal,
                draft_pr_title,
                draft_pr_body,
            ),
            "",
            "## Validation commands",
            *_item_lines("Commands", commands),
            "",
            "## Eval artifact",
            _eval_markdown(proposal) if proposal is not None else "No proposal eval artifact was attached.",
        ]
    )
    if suggested_replay_case is not None:
        lines.extend(
            [
                "",
                "## Suggested replay case",
                "Curate into `automation/feedback-loop/replay/corpus.json` after review "
                "(strip `suggested_by_run`).",
                "```json",
                json.dumps(suggested_replay_case, indent=2, sort_keys=True),
                "```",
            ]
        )
    lines.extend(
        [
            "",
            render_memory_metadata_block(metadata),
        ]
    )
    return "\n".join(lines).strip() + "\n"


def _builderbot_context_lines_for_optional_proposal(
    proposal: Proposal | None,
    draft_pr_title: str,
    draft_pr_body: str,
) -> list[str]:
    if proposal is None:
        return ["- n/a"]
    return _builderbot_context_lines(proposal, draft_pr_title, draft_pr_body)


def _proposal_status_lines(proposal: Proposal | None) -> list[str]:
    if proposal is None:
        return [
            "- Proposal: `none`",
            "- Eval state: `n/a`",
        ]
    lines = [
        "- Proposal: `generated`",
        f"- Eval state: `{proposal.eval_state}`",
        f"- Eval passed: `{proposal.eval_passed}`",
    ]
    if proposal.learning_id:
        lines.extend(
            [
                f"- Learning id: `{proposal.learning_id}`",
                f"- Route id: `{proposal.route_id}`",
                f"- Route role: `{proposal.route_role}`",
                f"- Linked routes: `{', '.join(proposal.linked_route_destinations)}`",
            ]
        )
    return lines


def _issue_title(cluster: Cluster, proposal: Proposal | None) -> str:
    summary = (
        cluster.title
        or cluster.summary
        or ("" if proposal is None else proposal.summary)
        or cluster.slug
    )
    if len(summary) > 80:
        summary = f"{summary[:77].rstrip()}..."
    return f"Feedback cluster: {summary}"


def _issue_labels(destination: Destination | str | None, trigger_builderbot: bool) -> tuple[str, ...]:
    labels = ["feedback-loop"]
    if destination:
        labels.append(str(destination))
    if trigger_builderbot:
        labels.append(BUILDERBOT_APPROVAL_LABEL)
    return tuple(_dedupe(labels))


def _summary_text(cluster: Cluster, proposal: Proposal | None) -> str:
    if proposal is not None:
        return proposal.summary
    return cluster.summary or f"Feedback cluster `{cluster.slug}`."


def _decision_for_cluster(cluster: Cluster, proposal: Proposal | None) -> str:
    if cluster.decision:
        return cluster.decision
    if cluster.already_covered:
        return "already_covered"
    if proposal is not None:
        return "promote"
    if any(signal.manual_triage for signal in cluster.signals):
        return "gather_more_evidence"
    if cluster.suggested_destination == "test_or_linter":
        return "convert_to_mechanical_check"
    if cluster.rank > 0:
        return "promote"
    return "gather_more_evidence"


def _status_for_cluster(
    cluster: Cluster,
    proposal: Proposal | None,
) -> ClusterIssueStatus:
    if proposal is not None:
        return "proposal_drafted"
    if cluster.already_covered:
        return "adopted"
    return "needs_triage"


def _current_pr_numbers(
    cluster: Cluster,
    *,
    fallback_urls: list[str] | tuple[str, ...] = (),
) -> tuple[int, ...]:
    numbers = {signal.pr_number for signal in cluster.signals if signal.pr_number > 0}
    numbers.update(pr_numbers_from_urls(fallback_urls))
    return tuple(sorted(numbers))


def _resolution_counts(signals: list[NormalizedSignal]) -> dict[str, int]:
    return _signal_resolution_counts(signals)


def _coerce_resolution_counts(value: object) -> dict[str, int]:
    if not isinstance(value, dict):
        return _empty_resolution_counts()
    counts = _empty_resolution_counts()
    for key, count in value.items():
        counts[str(key)] = int(count)
    return counts


def _empty_resolution_counts() -> dict[str, int]:
    return {state: 0 for state in RESOLUTION_STATES}


def _coverage_paths(signals: list[NormalizedSignal]) -> tuple[str, ...]:
    paths: list[str] = []
    for signal in signals:
        if signal.resolution is not None:
            paths.extend(signal.resolution.coverage_paths)
    return tuple(_dedupe(paths))


def _resolution_evidence_ids(signals: list[NormalizedSignal]) -> list[str]:
    ids: list[str] = []
    for signal in signals:
        if signal.resolution is not None:
            ids.extend(signal.resolution.evidence_signal_ids)
    return _dedupe(ids)


def _timestamp(now: datetime | None) -> str:
    value = now or datetime.now(timezone.utc)
    if value.tzinfo is None:
        value = value.replace(tzinfo=timezone.utc)
    return value.astimezone(timezone.utc).isoformat().replace("+00:00", "Z")


def _required_str(payload: dict[str, Any], key: str) -> str:
    value = payload.get(key)
    if not isinstance(value, str) or not value:
        raise ValueError(f"missing {key}")
    return value


def _json_from_stdout(stdout: str) -> dict[str, Any]:
    content = stdout.strip()
    if not content:
        return {}
    return json.loads(content)


def _issues_from_payload(payload: dict[str, Any]) -> list[dict[str, Any]]:
    if isinstance(payload.get("issues"), list):
        return list(payload["issues"])
    issues = payload.get("issues")
    if isinstance(issues, dict) and isinstance(issues.get("nodes"), list):
        return list(issues["nodes"])
    data = payload.get("data")
    if isinstance(data, dict):
        return _issues_from_payload(data)
    return []


def _next_cursor(payload: dict[str, Any]) -> str | None:
    """Best-effort pagination cursor detection across sq agent-tools payload shapes."""
    for key in ("next_cursor", "nextCursor", "cursor"):
        value = payload.get(key)
        if isinstance(value, str) and value:
            return value
    page_info = payload.get("pageInfo") or payload.get("page_info")
    if isinstance(page_info, dict):
        has_next = page_info.get("hasNextPage") or page_info.get("has_next_page")
        end_cursor = page_info.get("endCursor") or page_info.get("end_cursor")
        if has_next and isinstance(end_cursor, str) and end_cursor:
            return end_cursor
    data = payload.get("data")
    if isinstance(data, dict):
        return _next_cursor(data)
    return None


def _issue_from_payload(payload: dict[str, Any]) -> dict[str, Any]:
    issue = payload.get("issue")
    if isinstance(issue, dict):
        return issue
    data = payload.get("data")
    if isinstance(data, dict):
        return _issue_from_payload(data)
    return payload


def _snapshot_from_issue(issue: dict[str, Any]) -> LinearIssueSnapshot:
    state = issue.get("state")
    labels = issue.get("labels", [])
    return LinearIssueSnapshot(
        identifier=_issue_identifier(issue),
        url=str(issue.get("url", "")),
        title=str(issue.get("title", "")),
        description=str(issue.get("description", "")),
        labels=tuple(_label_names(labels)),
        state=_state_name(state),
    )


def _issue_identifier(issue: dict[str, Any]) -> str:
    identifier = issue.get("identifier") or issue.get("id") or ""
    return str(identifier)


def _issue_url_from_payload(payload: dict[str, Any]) -> str:
    issue = _issue_from_payload(payload)
    url = issue.get("url")
    if isinstance(url, str):
        return url
    return ""


def _label_names(labels: object) -> list[str]:
    if isinstance(labels, dict):
        labels = labels.get("nodes", [])
    if not isinstance(labels, list):
        return []
    names: list[str] = []
    for label in labels:
        if isinstance(label, dict) and label.get("name"):
            names.append(str(label["name"]))
        elif isinstance(label, str):
            names.append(label)
    return names


def _state_name(state: object) -> str:
    if isinstance(state, dict) and state.get("name"):
        return str(state["name"])
    if isinstance(state, str):
        return state
    return ""
