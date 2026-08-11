"""Outcome reconciliation: sync Linear issue states from draft-PR results.

The memory layer only learns whether its guardrails land if issue states track what happened to
the draft PRs. Every generated draft PR body carries a stable `change-set:<sha16>` marker
(route_metadata), and the issue metadata stores the same id — reconciliation searches GitHub for
that marker and maps the PR state onto the feedback-loop workflow:

    merged            -> adopted  (Done)      + remove code-engine:approved
    closed, unmerged  -> rejected (Canceled)  + remove code-engine:approved
    open (incl draft) -> pr_open  (In Review)
"""

from __future__ import annotations

from dataclasses import dataclass, replace
from datetime import datetime, timezone
from typing import Any, Literal, Protocol

from .cluster_memory import (
    ClusterMemoryReader,
    ClusterMemoryRecord,
    read_linear_memory,
    replace_memory_metadata_block,
)
from .config import RunConfig
from .github import GitHubClient, GitHubError, parse_pull_request_url
from .linear_control import BUILDERBOT_APPROVAL_LABEL, linear_state_for_cluster_status

# Issue statuses that indicate an in-flight proposal worth checking against GitHub.
IN_FLIGHT_STATUSES = frozenset({"eval_passed", "pr_open"})

PR_STATE_TO_STATUS = {
    "merged": "adopted",
    "closed": "rejected",
    "open": "pr_open",
}
TERMINAL_PR_STATES = frozenset({"merged", "closed"})


class OutcomeWriter(Protocol):
    """Minimal Linear write surface for outcome transitions."""

    def update_issue_outcome(
        self,
        issue_identifier: str,
        *,
        state: str,
        description: str,
        remove_labels: tuple[str, ...] = (),
    ) -> str:
        """Update one issue and return its URL."""


@dataclass(frozen=True)
class OutcomeAction:
    """One reconciliation decision for one in-flight memory record."""

    idempotency_key: str
    issue_identifier: str
    issue_url: str
    change_set_id: str
    cluster_slug: str
    pr_url: str = ""
    pr_number: int = 0
    pr_state: Literal["merged", "closed", "open", "not_found", "error"] = "not_found"
    previous_status: str = ""
    new_status: str = ""
    new_linear_state: str = ""
    remove_approval_label: bool = False
    applied: bool = False
    skipped_reason: str = ""
    error: str = ""


@dataclass(frozen=True)
class OutcomeReconcileResult:
    """All reconciliation actions for one invocation."""

    actions: tuple[OutcomeAction, ...]
    counts: dict[str, int]
    dry_run: bool


def reconcile_outcomes(
    cfg: RunConfig,
    *,
    reader: ClusterMemoryReader | None,
    writer: OutcomeWriter | None,
    github: GitHubClient,
    now: datetime | None = None,
) -> OutcomeReconcileResult:
    """Check in-flight proposal issues against their draft PRs and apply state transitions."""
    read_result = read_linear_memory(dry_run=cfg.dry_run, reader=reader)
    candidates = sorted(
        (
            record
            for record in read_result.records
            if record.metadata.change_set_id
            and record.metadata.issue_status in IN_FLIGHT_STATUSES
        ),
        key=lambda record: record.idempotency_key,
    )[: max(cfg.limit, 0)]

    actions = tuple(
        _reconcile_record(cfg, record, github=github, writer=writer, now=now)
        for record in candidates
    )
    counts: dict[str, int] = {
        "candidates": len(candidates),
        "applied": sum(1 for action in actions if action.applied),
        "skipped": sum(1 for action in actions if action.skipped_reason),
        "errors": sum(1 for action in actions if action.error),
    }
    for action in actions:
        key = f"pr_{action.pr_state}"
        counts[key] = counts.get(key, 0) + 1
    return OutcomeReconcileResult(actions=actions, counts=counts, dry_run=cfg.dry_run)


def outcome_artifact(result: OutcomeReconcileResult) -> dict[str, Any]:
    """JSON-ready reconcile record for the run artifact."""
    return {
        "dry_run": result.dry_run,
        "counts": dict(result.counts),
        "actions": [
            {
                "idempotency_key": action.idempotency_key,
                "issue_identifier": action.issue_identifier,
                "issue_url": action.issue_url,
                "change_set_id": action.change_set_id,
                "cluster_slug": action.cluster_slug,
                "pr_url": action.pr_url,
                "pr_number": action.pr_number,
                "pr_state": action.pr_state,
                "previous_status": action.previous_status,
                "new_status": action.new_status,
                "new_linear_state": action.new_linear_state,
                "remove_approval_label": action.remove_approval_label,
                "applied": action.applied,
                "skipped_reason": action.skipped_reason,
                "error": action.error,
            }
            for action in result.actions
        ],
    }


def _reconcile_record(
    cfg: RunConfig,
    record: ClusterMemoryRecord,
    *,
    github: GitHubClient,
    writer: OutcomeWriter | None,
    now: datetime | None,
) -> OutcomeAction:
    base = OutcomeAction(
        idempotency_key=record.idempotency_key,
        issue_identifier=record.issue_identifier,
        issue_url=record.issue_url,
        change_set_id=record.metadata.change_set_id,
        cluster_slug=record.memory_slug,
        previous_status=record.metadata.issue_status,
    )
    try:
        hits = github.search_prs_by_change_set(cfg.repo, record.metadata.change_set_id)
    except GitHubError as err:
        return replace(base, pr_state="error", error=str(err))
    if not hits:
        return replace(base, pr_state="not_found", skipped_reason="pr_not_found")

    newest = max(hits, key=_hit_number)
    pr_url = str(newest.get("html_url") or newest.get("url") or "")
    try:
        ref = parse_pull_request_url(pr_url)
        status = github.pull_request_status(ref)
    except GitHubError as err:
        return replace(base, pr_state="error", pr_url=pr_url, error=str(err))

    pr_state: Literal["merged", "closed", "open"]
    if status["merged"]:
        pr_state = "merged"
    elif status["state"] == "closed":
        pr_state = "closed"
    else:
        pr_state = "open"
    new_status = PR_STATE_TO_STATUS[pr_state]
    action = replace(
        base,
        pr_url=status["html_url"] or pr_url,
        pr_number=int(status["number"]),
        pr_state=pr_state,
        new_status=new_status,
        new_linear_state=linear_state_for_cluster_status(new_status),
        remove_approval_label=pr_state in TERMINAL_PR_STATES,
    )
    if record.metadata.issue_status == new_status:
        return replace(action, skipped_reason="already_reconciled")
    if cfg.dry_run or writer is None:
        return action

    metadata = replace(
        record.metadata,
        issue_status=new_status,
        outcome_pr_url=action.pr_url,
        outcome_checked_at=_timestamp(now),
    )
    writer.update_issue_outcome(
        record.issue_identifier,
        state=action.new_linear_state,
        description=replace_memory_metadata_block(record.description, metadata),
        remove_labels=(BUILDERBOT_APPROVAL_LABEL,) if action.remove_approval_label else (),
    )
    return replace(action, applied=True)


def _hit_number(hit: dict[str, Any]) -> int:
    number = hit.get("number")
    return number if isinstance(number, int) else 0


def _timestamp(now: datetime | None) -> str:
    value = now or datetime.now(timezone.utc)
    if value.tzinfo is None:
        value = value.replace(tzinfo=timezone.utc)
    return value.astimezone(timezone.utc).isoformat().replace("+00:00", "Z")
