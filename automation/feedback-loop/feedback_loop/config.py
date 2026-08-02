"""Run configuration for the feedback loop.

Stateless by design (BKW-64): config carries only what a single ephemeral run needs. No datastore
handles, no checkpoints persisted to disk. Rerun idempotency is owned by Linear cluster-memory
reconciliation (see cluster_memory.py).
"""

from __future__ import annotations

from dataclasses import dataclass, field

DEFAULT_REPO = "squareup/wallet"

# Bump when the harvest/normalize schema changes so reruns are distinguishable. Part of the
# idempotency key (BKW-80/BKW-81 acceptance: running the same PR twice does not duplicate work).
HARVEST_VERSION = "2"


@dataclass(frozen=True)
class RunConfig:
    """Everything one feedback-loop invocation needs. No persistent state."""

    repo: str = DEFAULT_REPO
    # dry_run defaults True in the scaffold: never open PRs / Linear issues / comments.
    dry_run: bool = True
    harvest_version: str = HARVEST_VERSION
    # Backfill window (used by `run --backfill`).
    since: str | None = None
    until: str | None = None
    limit: int = 100
    # Single-PR mode (used by `run --pr`).
    pr_url: str | None = None
    # Optional local dry-run artifact bundle directory.
    output_dir: str | None = None
    # Local checkout root used for plan reality checks; resolved by the CLI (default: git toplevel).
    repo_root: str | None = None
    extra: dict = field(default_factory=dict)
