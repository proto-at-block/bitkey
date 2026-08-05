"""BKW-83 exclusion rules for non-actionable feedback."""

from __future__ import annotations

from dataclasses import dataclass, replace
import re
from typing import Pattern

from .models import Exclusion, ExclusionReason, NormalizedSignal, PrimaryClass


EXCLUSION_TAG = "excluded"
EXCLUDED_FEEDBACK_KINDS = {"issue_comment", "review_comment", "review", "bot_review"}
PROMOTABLE_CLASSES = {"miss", "ci_failure", "validation_failure", "post_merge_fix"}


@dataclass(frozen=True)
class ExclusionRule:
    reason: ExclusionReason
    primary_class: PrimaryClass
    summary: str
    tag: str
    summarize_as_context: bool
    pattern: Pattern[str]


EXCLUSION_RULES: tuple[ExclusionRule, ...] = (
    ExclusionRule(
        reason="style_nit",
        primary_class="nit",
        summary="style, wording, formatting, or spelling nit",
        tag="excluded:nit",
        summarize_as_context=False,
        pattern=re.compile(
            r"\b(nit|nitpick|typo|spelling|formatting|whitespace|punctuation|wording|grammar)\b",
            re.IGNORECASE,
        ),
    ),
    ExclusionRule(
        reason="product_decision",
        primary_class="product_decision",
        summary="product, UX, launch, copy, or scope decision",
        tag="excluded:product_decision",
        summarize_as_context=True,
        pattern=re.compile(
            r"\b(product|scope|ux|design|copy|user experience|pm|launch|requirement)\b"
            r"|\b(do we want|should we allow|should this be)\b",
            re.IGNORECASE,
        ),
    ),
    ExclusionRule(
        reason="not_actionable",
        primary_class="not_actionable",
        summary="outside agent control or one-off operational noise",
        tag="excluded:not_actionable",
        summarize_as_context=True,
        pattern=re.compile(
            r"\b(flaky|flake|infra|infrastructure|github outage|external outage|runner|retry ci|rerun)\b",
            re.IGNORECASE,
        ),
    ),
    ExclusionRule(
        reason="subjective_preference",
        primary_class="preference",
        summary="subjective naming or implementation preference",
        tag="excluded:preference",
        summarize_as_context=True,
        pattern=re.compile(
            r"\b(prefer|preference|i'd rather|i would rather|maybe use|could use|consider using|rename|naming|name this)\b",
            re.IGNORECASE,
        ),
    ),
    ExclusionRule(
        reason="speculative_question",
        primary_class="question",
        summary="speculative question without deterministic follow-up evidence",
        tag="excluded:question",
        summarize_as_context=True,
        pattern=re.compile(
            r"\?\s*$|\b(why|what if|do we need|can we|could we|should we|is this|are we)\b",
            re.IGNORECASE,
        ),
    ),
)


def apply_exclusion(signal: NormalizedSignal) -> NormalizedSignal:
    """Attach auditable exclusion metadata when feedback is not guardrail material."""
    if signal.kind not in EXCLUDED_FEEDBACK_KINDS:
        return signal

    rule = _matching_rule(signal)
    if rule is None:
        return signal

    exclusion = Exclusion(
        reason=rule.reason,
        summary=rule.summary,
        summarize_as_context=rule.summarize_as_context,
        tags=[rule.tag],
    )
    return replace(
        signal,
        exclusion=exclusion,
        primary_class=rule.primary_class,
        secondary_tags=_dedupe([*signal.secondary_tags, EXCLUSION_TAG, rule.tag]),
        rationale=_append_rationale(signal.rationale, f"excluded: {rule.summary}"),
        suggested_destination=None,
        manual_triage=False,
    )


def matches_exclusion_pattern(signal: NormalizedSignal) -> bool:
    """Return whether feedback text matches an explicit exclusion pattern."""
    return _matching_rule(signal, respect_primary_class=False) is not None


def _matching_rule(
    signal: NormalizedSignal,
    *,
    respect_primary_class: bool = True,
) -> ExclusionRule | None:
    body = signal.body.strip()
    if not body:
        return None
    if respect_primary_class and signal.primary_class in PROMOTABLE_CLASSES:
        return None

    for rule in EXCLUSION_RULES:
        if rule.pattern.search(body):
            return rule
    return None


def _append_rationale(existing: str, addition: str) -> str:
    return f"{existing}; {addition}" if existing else addition


def _dedupe(values: list[str]) -> list[str]:
    deduped: list[str] = []
    seen: set[str] = set()
    for value in values:
        if value in seen:
            continue
        seen.add(value)
        deduped.append(value)
    return deduped
