"""Run configuration for the feedback loop.

Stateless by design (BKW-64): config carries only what a single ephemeral run needs. No datastore
handles, no checkpoints persisted to disk. Reruns reconcile by using a stable key derived from
(repo, PR number, harvest version).
"""

from __future__ import annotations

from dataclasses import dataclass, field

DEFAULT_REPO = "squareup/wallet"

# Bump when the harvest/normalize schema changes so reruns are distinguishable. Part of the
# idempotency key (BKW-80/BKW-81 acceptance: running the same PR twice does not duplicate work).
HARVEST_VERSION = "1"


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
    extra: dict = field(default_factory=dict)

    def idempotency_key(self, pr_number: int) -> str:
        """Stable run key for stateless retries and overlapping backfills."""
        if pr_number <= 0:
            raise ValueError("pr_number must be positive")
        repo = self.repo.strip().lower()
        harvest_version = self.harvest_version.strip()
        if not repo or "/" not in repo:
            raise ValueError("repo must be OWNER/REPO")
        if not harvest_version:
            raise ValueError("harvest_version must be non-empty")
        return f"{repo}/pr/{pr_number}/harvest-v{harvest_version}"
