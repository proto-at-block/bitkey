"""Deterministic facts layer: objective structure the LLM classifier reasons over.

Only pure-metadata judgments are made deterministically: check signals (ci_failure vs
validation_failure from the check source), thread structure, timestamp ordering, diff membership,
and head tracking. Everything interpretive (taxonomy, severity, exclusions, resolution) belongs
to the LLM classification stage — keyword heuristics misread review language and tie outcomes to
phrasing rather than meaning.
"""

from __future__ import annotations

from dataclasses import dataclass, replace

from ..config import RunConfig
from ..models import (
    CheckFact,
    CommitFact,
    NormalizedSignal,
    PrFacts,
    PrimaryClass,
    SignalFacts,
)
from ..util import dedupe, is_after

FEEDBACK_KINDS = frozenset({"issue_comment", "review_comment", "review", "bot_review"})
CHANGE_KINDS = frozenset({"changed_file", "diff_hunk"})
TRUSTED_AUTHOR_ASSOCIATIONS = frozenset({"OWNER", "MEMBER", "COLLABORATOR"})


@dataclass(frozen=True)
class SignalIndex:
    """Pre-indexed signals for fact lookups within one pipeline run."""

    by_kind: dict[str, list[NormalizedSignal]]
    changes_by_path: dict[str, list[NormalizedSignal]]
    feedback: list[NormalizedSignal]
    pr_metadata_by_pr: dict[int, list[NormalizedSignal]]


@dataclass(frozen=True)
class FactsResult:
    """Signals with facts attached plus per-PR fact bundles."""

    signals: list[NormalizedSignal]
    pr_facts: dict[int, PrFacts]


def attach_facts(cfg: RunConfig, signals: list[NormalizedSignal]) -> FactsResult:
    """Attach SignalFacts to feedback signals and classify check signals deterministically."""
    index = build_signal_index(signals)
    pr_facts = _build_pr_facts(signals, index)
    enriched: list[NormalizedSignal] = []
    for signal in signals:
        if signal.kind == "check":
            enriched.append(_classified_check(signal))
        elif signal.kind in FEEDBACK_KINDS:
            enriched.append(
                replace(signal, facts=_facts_for_feedback(signal, index, pr_facts))
            )
        else:
            enriched.append(signal)
    return FactsResult(signals=enriched, pr_facts=pr_facts)


def build_signal_index(signals: list[NormalizedSignal]) -> SignalIndex:
    by_kind: dict[str, list[NormalizedSignal]] = {}
    changes_by_path: dict[str, list[NormalizedSignal]] = {}
    feedback: list[NormalizedSignal] = []
    pr_metadata_by_pr: dict[int, list[NormalizedSignal]] = {}

    for signal in signals:
        by_kind.setdefault(signal.kind, []).append(signal)
        if signal.kind in CHANGE_KINDS and signal.path:
            changes_by_path.setdefault(signal.path, []).append(signal)
        if signal.kind in FEEDBACK_KINDS:
            feedback.append(signal)
        if signal.kind == "pr_metadata":
            pr_metadata_by_pr.setdefault(signal.pr_number, []).append(signal)

    return SignalIndex(
        by_kind=by_kind,
        changes_by_path=changes_by_path,
        feedback=feedback,
        pr_metadata_by_pr=pr_metadata_by_pr,
    )


def check_primary_class(signal: NormalizedSignal) -> PrimaryClass:
    """Deterministic carve-out: check signals are pure metadata, not interpretation."""
    source = _string_value(signal.raw_metadata.get("source")).casefold()
    name = _string_value(signal.raw_metadata.get("name")).casefold()
    if "validation" in source or "validation" in name or "ai-context-check" in name:
        return "validation_failure"
    return "ci_failure"


def _classified_check(signal: NormalizedSignal) -> NormalizedSignal:
    primary_class = check_primary_class(signal)
    tags = ["validation"] if primary_class == "validation_failure" else ["ci"]
    return replace(
        signal,
        primary_class=primary_class,
        severity="medium",
        confidence=0.9,
        rationale="failed validation signal" if primary_class == "validation_failure" else "failed CI signal",
        suggested_destination="test_or_linter",
        secondary_tags=dedupe([*signal.secondary_tags, *tags]),
        area=signal.area or "repo-wide",
    )


def _facts_for_feedback(
    signal: NormalizedSignal,
    index: SignalIndex,
    pr_facts: dict[int, PrFacts],
) -> SignalFacts:
    facts = pr_facts.get(signal.pr_number)
    later_replies = [
        item.source_id
        for item in index.feedback
        if item.pr_number == signal.pr_number
        and item.source_id != signal.source_id
        and is_after(item.created_at, signal.created_at)
        and _same_feedback_thread(signal, item)
    ]
    later_commits = [
        item.source_id
        for item in index.by_kind.get("commit", [])
        if item.pr_number == signal.pr_number and is_after(item.created_at, signal.created_at)
    ]
    later_failed_checks = [
        item.source_id
        for item in index.by_kind.get("check", [])
        if item.pr_number == signal.pr_number and is_after(item.created_at, signal.created_at)
    ]
    path_changes = [
        item
        for item in index.changes_by_path.get(signal.path or "", [])
        if item.pr_number == signal.pr_number
    ]
    reviewed_head_sha = _string_value(signal.raw_metadata.get("reviewed_head_sha"))
    final_head_sha = "" if facts is None else facts.head_sha

    return SignalFacts(
        thread_id=_string_value(signal.raw_metadata.get("thread_id")),
        in_reply_to_source_id=_reply_parent(signal),
        is_reply=bool(_reply_parent(signal)),
        later_reply_source_ids=tuple(later_replies),
        thread_resolved=(
            signal.raw_metadata.get("thread_resolved") is True
            or signal.raw_metadata.get("resolved") is True
        ),
        later_commit_source_ids=tuple(later_commits),
        later_failed_check_source_ids=tuple(later_failed_checks),
        path_in_diff=bool(path_changes),
        line_in_changed_hunk=signal.line is not None
        and any(_line_inside_hunk(signal.line, item) for item in path_changes),
        reviewed_head_sha=reviewed_head_sha,
        final_head_sha=final_head_sha,
        reviewed_earlier_head=bool(
            reviewed_head_sha and final_head_sha and reviewed_head_sha != final_head_sha
        ),
        author_is_bot=signal.is_bot,
        author_trusted=(
            signal.is_bot or signal.author_association.upper() in TRUSTED_AUTHOR_ASSOCIATIONS
        ),
    )


def _build_pr_facts(
    signals: list[NormalizedSignal],
    index: SignalIndex,
) -> dict[int, PrFacts]:
    facts: dict[int, PrFacts] = {}
    for pr_number, metadata_signals in index.pr_metadata_by_pr.items():
        metadata = metadata_signals[0]
        shas = metadata.raw_metadata.get("shas")
        shas = shas if isinstance(shas, dict) else {}
        timestamps = metadata.raw_metadata.get("timestamps")
        timestamps = timestamps if isinstance(timestamps, dict) else {}
        changed_paths = sorted(
            {
                item.path
                for item in index.by_kind.get("changed_file", [])
                if item.pr_number == pr_number and item.path
            }
        )
        commits = tuple(
            CommitFact(
                source_id=item.source_id,
                sha=_string_value(item.raw_metadata.get("sha")),
                created_at=item.created_at,
                message_first_line=item.body.splitlines()[0] if item.body else "",
            )
            for item in index.by_kind.get("commit", [])
            if item.pr_number == pr_number
        )
        failed_checks = tuple(
            CheckFact(
                source_id=item.source_id,
                name=_string_value(item.raw_metadata.get("name")),
                conclusion=_string_value(item.raw_metadata.get("conclusion")),
                completed_at=item.created_at,
                primary_class=check_primary_class(item),
            )
            for item in index.by_kind.get("check", [])
            if item.pr_number == pr_number
        )
        facts[pr_number] = PrFacts(
            pr_number=pr_number,
            repo=metadata.repo,
            pr_url=metadata.source_url,
            merged_at=_string_value(timestamps.get("merged_at")),
            base_sha=_string_value(shas.get("base")),
            head_sha=_string_value(shas.get("head")),
            merge_sha=_string_value(shas.get("merge_commit")),
            changed_paths=tuple(changed_paths),
            commits=commits,
            failed_checks=failed_checks,
        )
    return facts


def _same_feedback_thread(feedback: NormalizedSignal, reply: NormalizedSignal) -> bool:
    if feedback.pr_number != reply.pr_number:
        return False

    reply_to_source_id = _string_value(reply.raw_metadata.get("reply_to_source_id"))
    if reply_to_source_id and reply_to_source_id == feedback.source_id:
        return True

    feedback_id = _string_value(feedback.raw_metadata.get("id"))
    reply_parent_id = _string_value(reply.raw_metadata.get("in_reply_to_id"))
    if feedback_id and reply_parent_id and feedback_id == reply_parent_id:
        return True

    feedback_parent_id = _string_value(feedback.raw_metadata.get("in_reply_to_id"))
    if feedback_parent_id and reply_parent_id and feedback_parent_id == reply_parent_id:
        return True

    feedback_thread_id = _string_value(feedback.raw_metadata.get("thread_id"))
    reply_thread_id = _string_value(reply.raw_metadata.get("thread_id"))
    return bool(feedback_thread_id and feedback_thread_id == reply_thread_id)


def _reply_parent(signal: NormalizedSignal) -> str:
    return _string_value(signal.raw_metadata.get("reply_to_source_id")) or _string_value(
        signal.raw_metadata.get("in_reply_to_id")
    )


def _line_inside_hunk(line: int, signal: NormalizedSignal) -> bool:
    if signal.kind != "diff_hunk":
        return False
    new_start = signal.raw_metadata.get("new_start")
    new_count = signal.raw_metadata.get("new_count")
    if not isinstance(new_start, int) or not isinstance(new_count, int) or new_count <= 0:
        return False
    return new_start <= line <= new_start + new_count - 1


def _string_value(value: object) -> str:
    return value if isinstance(value, str) else ""
