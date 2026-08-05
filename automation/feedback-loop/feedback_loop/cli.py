"""CLI entrypoint + stage orchestration for the feedback loop.

Two entrypoints:
  run --pr <url>     process a single merged PR (the form the Builderbot/Blox trigger invokes)
  run --backfill     process a bounded historical window

The orchestration wiring is real so the end-to-end control flow, idempotency, and dry-run safety
are reviewable now. The stages themselves are stubbed (see feedback_loop/pipeline/).
"""

from __future__ import annotations

import argparse
import sys
from typing import Sequence

from .config import RunConfig
from .eval_gate import ProposalEvalBlocked, require_pr_ready
from .models import Cluster, Proposal, RawSignal
from .pipeline import classify, cluster, emit, harvest, normalize, propose, triage


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
        help="Allow writes. Still human-gated: creates Linear issues for Builderbot pickup.",
    )
    return parser


def _process_pr(cfg: RunConfig, pr_url: str) -> int:
    """Run the full stage chain for one PR. Scaffolded stages fail loudly until implemented."""
    raw = harvest.harvest_pr(cfg, pr_url)
    return _run_pipeline(cfg, raw)


def _process_backfill(cfg: RunConfig, pr_urls: list[str]) -> int:
    """Run each PR independently, then merge clusters for the bounded historical window."""
    per_pr_clusters: list[Cluster] = []
    for url in pr_urls:
        per_pr_clusters.extend(_clusters_for_raw(cfg, harvest.harvest_pr(cfg, url)))
    return _run_cluster_outputs(cfg, cluster.merge_clusters(cfg, per_pr_clusters))


def _run_pipeline(cfg: RunConfig, raw: list[RawSignal]) -> int:
    return _run_cluster_outputs(cfg, _clusters_for_raw(cfg, raw))


def _clusters_for_raw(cfg: RunConfig, raw: list[RawSignal]) -> list[Cluster]:
    norm = normalize.normalize(cfg, raw)
    classified = classify.classify(cfg, norm)
    return cluster.cluster(cfg, classified)


def _run_cluster_outputs(cfg: RunConfig, clusters: list[Cluster]) -> int:
    report = triage.build_triage_report(clusters)
    _write_triage_report(cfg, report.markdown)
    proposals = propose.propose(cfg, clusters)
    ready_proposals = _pr_ready_proposals(cfg, proposals)
    if not ready_proposals:
        return 0
    if not cfg.dry_run:
        raise NotImplementedError("feedback-loop Linear writer is not wired yet")
    emit.emit(cfg, ready_proposals)
    return 0


def _pr_ready_proposals(cfg: RunConfig, proposals: list[Proposal]) -> list[Proposal]:
    ready: list[Proposal] = []
    blocked: list[ProposalEvalBlocked] = []
    for proposal in proposals:
        try:
            require_pr_ready(proposal)
        except ProposalEvalBlocked as err:
            blocked.append(err)
        else:
            ready.append(proposal)
    if blocked:
        _write_eval_blocked_summary(len(blocked))
        if not cfg.dry_run:
            raise blocked[0]
    return ready


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


def main(argv: Sequence[str] | None = None) -> int:
    args = _build_parser().parse_args(list(argv) if argv is not None else None)

    cfg = RunConfig(
        repo=args.repo,
        dry_run=args.dry_run,
        since=args.since,
        until=args.until,
        limit=args.limit,
        pr_url=args.pr_url,
    )

    try:
        if args.pr_url:
            return _process_pr(cfg, args.pr_url)
        # --backfill: enumerate merged PRs and cluster the whole bounded window together.
        pr_urls = harvest.list_merged_prs(cfg)
        return _process_backfill(cfg, pr_urls)
    except harvest.HarvestError as err:
        sys.stderr.write(f"feedback_loop: harvest failed: {err}\n")
        return 2
    except NotImplementedError as err:
        # Expected in the scaffold: surface which stage is not built yet.
        sys.stderr.write(f"feedback_loop: stage not implemented yet: {err}\n")
        return 3
    except ProposalEvalBlocked as err:
        sys.stderr.write(f"feedback_loop: proposal eval blocked: {err}\n")
        return 3
