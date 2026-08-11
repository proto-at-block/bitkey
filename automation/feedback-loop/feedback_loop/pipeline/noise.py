"""Deterministic prefilter for pure bot/process noise.

Only mechanical automation chatter is filtered here (Linear linkbacks, owner-table and
merge-gatekeeper status comments, contentless bot review wrappers, agent acknowledgement
replies). Subjective judgments — nits, preferences, product decisions, speculative questions —
are LLM classifications, not regexes.
"""

from __future__ import annotations

from dataclasses import dataclass, replace
import re
from typing import Pattern

from ..models import Exclusion, ExclusionReason, NormalizedSignal
from ..util import dedupe

NOISE_FEEDBACK_KINDS = frozenset({"issue_comment", "review_comment", "review", "bot_review"})

FIXED_REPLY_RE = re.compile(
    r"\b(addressed|fixed|resolved|done|updated|added|covered|implemented)\b",
    re.IGNORECASE,
)
TOP_LEVEL_ACK_RE = re.compile(
    r"^\s*(?:🤖\s*)?(addressed|fixed|resolved|done|updated|added|covered|implemented)\b",
    re.IGNORECASE,
)
NEW_FINDING_AFTER_ACK_RE = re.compile(
    r"\b(but|however|still)\b.*"
    r"\b(missing|bug|broken|fail|failing|incorrect|regression|uncovered|not covered)\b",
    re.IGNORECASE,
)


@dataclass(frozen=True)
class NoiseRule:
    reason: ExclusionReason
    summary: str
    tag: str
    pattern: Pattern[str]


NOISE_RULES: tuple[NoiseRule, ...] = (
    NoiseRule(
        reason="not_actionable",
        summary="Linear linkback/status comment",
        tag="excluded:linear_linkback",
        pattern=re.compile(
            r"\b(linear issue|linked linear|linear linkback|created linear|updated linear)\b"
            r"|https://linear\.app/",
            re.IGNORECASE,
        ),
    ),
    NoiseRule(
        reason="not_actionable",
        summary="Owner Owl or owner-table status comment",
        tag="excluded:owner_status",
        pattern=re.compile(
            r"\b(owner owl|owner-?table|owner table|code owner routing|ownership table)\b",
            re.IGNORECASE,
        ),
    ),
    NoiseRule(
        reason="not_actionable",
        summary="Merge Gatekeeper status comment",
        tag="excluded:merge_gatekeeper",
        pattern=re.compile(
            r"\b(merge gatekeeper|merge-gatekeeper|merge queue status|merge blocked by status)\b",
            re.IGNORECASE,
        ),
    ),
    NoiseRule(
        reason="not_actionable",
        summary="generic Codex wrapper review without concrete finding",
        tag="excluded:codex_wrapper",
        pattern=re.compile(
            r"\bcodex\b(?!.{0,60}\bsecurity review\b).{0,160}"
            r"\b(reviewed this pr|review complete|completed review|no issues found|left comments|generated review)\b",
            re.IGNORECASE | re.DOTALL,
        ),
    ),
)

ACKNOWLEDGEMENT_RULE = NoiseRule(
    reason="not_actionable",
    summary="agent acknowledgement that feedback was fixed or addressed",
    tag="excluded:agent_ack",
    pattern=FIXED_REPLY_RE,
)


def prefilter(
    signals: list[NormalizedSignal],
) -> tuple[list[NormalizedSignal], list[NormalizedSignal]]:
    """Split signals into (kept, noise); noise keeps auditable Exclusion metadata.

    Acknowledgement replies are noise for classification purposes, but their bodies still inform
    resolution judgment through SignalFacts.later_reply_source_ids excerpts.
    """
    kept: list[NormalizedSignal] = []
    noise: list[NormalizedSignal] = []
    for signal in signals:
        rule = _matching_rule(signal)
        if rule is None:
            kept.append(signal)
        else:
            noise.append(_excluded(signal, rule))
    return kept, noise


def _matching_rule(signal: NormalizedSignal) -> NoiseRule | None:
    if signal.kind not in NOISE_FEEDBACK_KINDS or not signal.body.strip():
        return None
    if _is_acknowledgement(signal):
        return ACKNOWLEDGEMENT_RULE
    for rule in NOISE_RULES:
        if rule.pattern.search(signal.body):
            return rule
    return None


def _is_acknowledgement(signal: NormalizedSignal) -> bool:
    if not FIXED_REPLY_RE.search(signal.body):
        return False
    if NEW_FINDING_AFTER_ACK_RE.search(signal.body):
        return False
    return _has_reply_metadata(signal) or _is_top_level_agent_acknowledgement(signal)


def _is_top_level_agent_acknowledgement(signal: NormalizedSignal) -> bool:
    if not TOP_LEVEL_ACK_RE.search(signal.body):
        return False
    source_text = " ".join(
        [
            signal.source,
            signal.author,
            _string_value(signal.raw_metadata.get("source")),
            _string_value(signal.raw_metadata.get("provider")),
        ]
    ).casefold()
    return (
        signal.body.lstrip().startswith("🤖")
        or signal.is_bot
        or "bot" in source_text
        or "codex" in source_text
        or "builderbot" in source_text
    )


def _has_reply_metadata(signal: NormalizedSignal) -> bool:
    return bool(
        _string_value(signal.raw_metadata.get("reply_to_source_id"))
        or _string_value(signal.raw_metadata.get("in_reply_to_id"))
    )


def _excluded(signal: NormalizedSignal, rule: NoiseRule) -> NormalizedSignal:
    return replace(
        signal,
        exclusion=Exclusion(
            reason=rule.reason,
            summary=rule.summary,
            summarize_as_context=True,
            tags=[rule.tag],
        ),
        primary_class="not_actionable",
        severity="low",
        rationale=f"excluded: {rule.summary}",
        suggested_destination=None,
        manual_triage=False,
        secondary_tags=dedupe([*signal.secondary_tags, "excluded", rule.tag]),
    )


def _string_value(value: object) -> str:
    return value if isinstance(value, str) else ""
