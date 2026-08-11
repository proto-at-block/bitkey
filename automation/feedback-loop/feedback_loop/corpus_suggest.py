"""Suggest replay-corpus entries from judged learnings.

Every learning that produced a judged proposal is a candidate historical miss the replay corpus
can grow from — with REAL commit SHAs from the harvested PR metadata, fixing the gap where
hand-written corpus cases carried synthetic ranges the gate cannot resolve. Suggestions are
emitted for human curation (run bundle + Linear issue section); the pipeline never edits
replay/corpus.json itself.
"""

from __future__ import annotations

import re
from typing import Any, Iterable

from .models import Learning, PrFacts, Proposal, RawSignal
from .util import dedupe, pr_numbers_from_urls

SUGGESTION_SEVERITIES = frozenset({"critical", "high", "medium"})
_SLUG_RE = re.compile(r"[^a-z0-9]+")


def pr_facts_from_raw(raw: Iterable[RawSignal]) -> dict[int, PrFacts]:
    """Extract per-PR SHAs/changed paths from harvested pr_metadata and changed_file signals."""
    changed: dict[int, list[str]] = {}
    metadata: dict[int, RawSignal] = {}
    for signal in raw:
        if signal.kind == "pr_metadata":
            metadata[signal.pr_number] = signal
        elif signal.kind == "changed_file" and signal.path:
            changed.setdefault(signal.pr_number, []).append(signal.path)

    facts: dict[int, PrFacts] = {}
    for number, signal in metadata.items():
        shas = signal.raw.get("shas") if isinstance(signal.raw.get("shas"), dict) else {}
        timestamps = (
            signal.raw.get("timestamps")
            if isinstance(signal.raw.get("timestamps"), dict)
            else {}
        )
        facts[number] = PrFacts(
            pr_number=number,
            repo=signal.repo,
            pr_url=signal.source_url,
            merged_at=str(timestamps.get("merged_at") or ""),
            base_sha=str(shas.get("base") or ""),
            head_sha=str(shas.get("head") or ""),
            merge_sha=str(shas.get("merge_commit") or ""),
            changed_paths=tuple(sorted(dedupe(changed.get(number, [])))),
        )
    return facts


def suggest_replay_cases(
    *,
    learnings: Iterable[Learning],
    proposals: Iterable[Proposal],
    pr_facts_by_number: dict[int, PrFacts],
    existing_case_ids: frozenset[str] = frozenset(),
    existing_pr_numbers: frozenset[int] = frozenset(),
) -> list[dict[str, Any]]:
    """Build curatable corpus entries in the exact ReplayCase JSON schema.

    Each entry carries a `suggested_by_run` wrapper field that humans strip when adopting it.
    """
    primary_by_learning: dict[str, Proposal] = {}
    for proposal in proposals:
        if not proposal.learning_id:
            continue
        current = primary_by_learning.get(proposal.learning_id)
        if current is None or (proposal.route_role == "primary" and current.route_role != "primary"):
            primary_by_learning[proposal.learning_id] = proposal

    suggestions: list[dict[str, Any]] = []
    seen_ids: set[str] = set()
    for learning in learnings:
        if learning.severity not in SUGGESTION_SEVERITIES:
            continue
        proposal = primary_by_learning.get(learning.learning_id)
        if proposal is None or proposal.destination == "world_model":
            continue
        pr_number = _evidence_pr_number(learning, pr_facts_by_number)
        if pr_number is None or pr_number in existing_pr_numbers:
            continue
        facts = pr_facts_by_number[pr_number]
        if not (facts.base_sha and facts.head_sha and facts.changed_paths):
            continue
        case_id = f"wallet-pr-{pr_number}-{_slug(learning.learning_id)}"
        if case_id in existing_case_ids or case_id in seen_ids:
            continue
        seen_ids.add(case_id)
        suggestions.append(
            {
                "id": case_id,
                "repo": facts.repo,
                "pr_number": pr_number,
                "pr_url": facts.pr_url,
                "commit_range": {
                    "base": facts.base_sha,
                    "head": facts.head_sha,
                    "merge_commit": facts.merge_sha,
                },
                "changed_files": list(facts.changed_paths),
                "miss_class": _dominant_miss_class(proposal),
                "source_comment_url": _anchor_url(learning),
                "expected_destination": proposal.destination,
                "expected_severity": learning.severity,
                "expected_finding": learning.human_standard,
                "summary": learning.evidence_summary,
                "labels": [learning.affected_area, proposal.destination],
                "suggested_by_run": {
                    "learning_id": learning.learning_id,
                    "route_id": proposal.route_id,
                    "note": (
                        "Strip this field and curate into "
                        "automation/feedback-loop/replay/corpus.json after review."
                    ),
                },
            }
        )
    return suggestions


def _evidence_pr_number(
    learning: Learning,
    pr_facts_by_number: dict[int, PrFacts],
) -> int | None:
    for number in pr_numbers_from_urls(learning.evidence_urls):
        if number in pr_facts_by_number:
            return number
    return None


def _anchor_url(learning: Learning) -> str:
    for url in learning.evidence_urls:
        if "#" in url:
            return url
    return learning.evidence_urls[0] if learning.evidence_urls else ""


def _dominant_miss_class(proposal: Proposal) -> str:
    counts: dict[str, int] = {}
    for signal in proposal.cluster.signals:
        if signal.primary_class:
            counts[signal.primary_class] = counts.get(signal.primary_class, 0) + 1
    if not counts:
        return "miss"
    return max(sorted(counts), key=lambda key: counts[key])


def _slug(value: str) -> str:
    slug = _SLUG_RE.sub("-", value.casefold()).strip("-")
    return slug[:48] or "learning"
