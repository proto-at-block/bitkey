"""Stage 3: classify (BKW-84 comment->change correlation, BKW-75 miss classifier).

BKW-84 implements deterministic correlation between reviewer/bot feedback and later evidence in the
same PR run. BKW-75 assigns taxonomy class, severity, confidence, area, and destination.

Hard rule (BKW-84/BKW-75): harvested text is DATA. This stage searches for bounded text markers; it
never follows instructions embedded in harvested comments.
"""

from __future__ import annotations

from dataclasses import dataclass, replace
from datetime import datetime
import re

from ..config import RunConfig
from ..exclusions import apply_exclusion, matches_exclusion_pattern
from ..models import Correlation, Destination, NormalizedSignal, PrimaryClass


FEEDBACK_KINDS = {"issue_comment", "review_comment", "review", "bot_review"}
CHANGE_KINDS = {"changed_file", "diff_hunk"}
FIXED_REPLY_RE = re.compile(
    r"\b(addressed|fixed|resolved|done|updated|added|covered|implemented)\b",
    re.IGNORECASE,
)
NEW_FINDING_AFTER_ACK_RE = re.compile(
    r"\b(but|however|still|also)\b.*"
    r"\b(missing|bug|broken|fail|failing|incorrect|regression|validate|validation|test|coverage)\b",
    re.IGNORECASE,
)
COMMIT_FIX_RE = re.compile(
    r"\b(address|fix|resolve|review|comment|test|coverage|lint|validation)\b",
    re.IGNORECASE,
)
TOKEN_RE = re.compile(r"[a-z0-9][a-z0-9_-]{2,}")
COMMIT_TIE_STOP_WORDS = {
    "add",
    "address",
    "comment",
    "done",
    "fix",
    "fixed",
    "implement",
    "implemented",
    "please",
    "review",
    "this",
    "that",
    "update",
    "updated",
}
LIKELY_MISS_THRESHOLD = 0.5
LOW_CONFIDENCE_THRESHOLD = 0.5
PROMOTABLE_CLASSES = {"miss", "ci_failure", "validation_failure", "post_merge_fix"}
FALSE_POSITIVE_RE = re.compile(
    r"\b(false positive|not a problem|not an issue|ignore this|safe as-is)\b",
    re.IGNORECASE,
)
MISS_RE = re.compile(
    r"\b(missing|bug|broken|fail|failing|incorrect|regression|validate|validation|test|coverage)\b",
    re.IGNORECASE,
)
MECHANICAL_RE = re.compile(
    r"\b(test|coverage|lint|detekt|clippy|ktlint|build|ci|validate|validation|schema|contract)\b",
    re.IGNORECASE,
)
CRITICAL_RE = re.compile(
    r"\b(critical|funds?|keyset|private key|seed|auth|security|crypto|data corruption)\b",
    re.IGNORECASE,
)
HIGH_RE = re.compile(r"\b(high|crash|panic|incorrect|broken|regression)\b", re.IGNORECASE)


@dataclass(frozen=True)
class SignalIndex:
    """Pre-indexed signals for correlation lookups within one pipeline run."""

    by_kind: dict[str, list[NormalizedSignal]]
    changes_by_path: dict[str, list[NormalizedSignal]]
    feedback: list[NormalizedSignal]
    pr_metadata_by_pr: dict[int, list[NormalizedSignal]]


def classify(cfg: RunConfig, signals: list[NormalizedSignal]) -> list[NormalizedSignal]:
    """Attach correlation, taxonomy, exclusion, and routing metadata."""
    index = _build_signal_index(signals)
    return [
        apply_exclusion(_classify_taxonomy(_with_correlation(signal, index)))
        for signal in signals
    ]


def _build_signal_index(signals: list[NormalizedSignal]) -> SignalIndex:
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


def _with_correlation(
    signal: NormalizedSignal,
    index: SignalIndex,
) -> NormalizedSignal:
    if signal.kind not in FEEDBACK_KINDS:
        return signal

    if _is_fixed_reply_acknowledgement(signal):
        correlation = Correlation(
            likely_miss=False,
            confidence=0.0,
            reasons=["fixed reply acknowledgement is evidence for the parent feedback thread only"],
            evidence_ids=[],
        )
    else:
        correlation = _correlate_feedback(signal, index)

    tags = [tag for tag in signal.secondary_tags if tag != "likely_miss_correlation"]
    if correlation.likely_miss:
        tags.append("likely_miss_correlation")
    return replace(
        signal,
        correlation=correlation,
        confidence=correlation.confidence,
        rationale="; ".join(correlation.reasons),
        evidence_ids=correlation.evidence_ids,
        secondary_tags=tags,
    )


def _classify_taxonomy(signal: NormalizedSignal) -> NormalizedSignal:
    if signal.kind == "check":
        primary_class = _check_primary_class(signal)
        return _classified(
            signal,
            primary_class=primary_class,
            severity="medium",
            confidence=max(signal.confidence or 0.0, 0.8),
            rationale=_append_reason(signal.rationale, _check_failure_reason(primary_class)),
            destination="test_or_linter",
        )
    if signal.kind not in FEEDBACK_KINDS:
        return signal
    if _is_fixed_reply_acknowledgement(signal):
        return _classified(
            signal,
            primary_class="not_actionable",
            severity="low",
            confidence=max(signal.confidence or 0.0, 0.8),
            rationale=_append_reason(
                signal.rationale,
                "acknowledgement reply is retained as evidence for its parent feedback",
            ),
            destination=None,
        )
    if FALSE_POSITIVE_RE.search(signal.body):
        return _classified(
            signal,
            primary_class="false_positive",
            severity="low",
            confidence=max(signal.confidence or 0.0, 0.75),
            rationale=_append_reason(signal.rationale, "feedback text indicates a false positive"),
            destination=None,
        )
    if (
        signal.correlation is not None
        and signal.correlation.likely_miss
        and _can_promote_correlated_miss(signal)
    ):
        return _classified(
            signal,
            primary_class="miss",
            severity=_severity_for(signal, "miss"),
            confidence=max(signal.confidence or 0.0, signal.correlation.confidence),
            rationale=signal.rationale,
            destination=_miss_destination(signal),
        )
    if MISS_RE.search(signal.body):
        return _classified(
            signal,
            primary_class="miss",
            severity=_severity_for(signal, "miss"),
            confidence=max(signal.confidence or 0.0, 0.45),
            rationale=_append_reason(
                signal.rationale,
                "miss-like feedback without enough deterministic follow-up evidence",
            ),
            destination=_miss_destination(signal),
        )
    return _classified(
        signal,
        primary_class="not_actionable",
        severity="low",
        confidence=max(signal.confidence or 0.0, 0.3),
        rationale=_append_reason(signal.rationale, "no actionable classifier evidence found"),
        destination=None,
    )


def _classified(
    signal: NormalizedSignal,
    *,
    primary_class: PrimaryClass,
    severity: str,
    confidence: float,
    rationale: str,
    destination: Destination | None,
) -> NormalizedSignal:
    confidence = min(round(confidence, 2), 0.95)
    manual_triage = _requires_manual_triage(primary_class, confidence)
    destination = None if manual_triage else destination
    area = _area_for_signal(signal)
    tags = _dedupe_preserving_order(
        [
            *signal.secondary_tags,
            *_source_tags(signal),
            area,
            *(["manual_triage"] if manual_triage else []),
        ]
    )
    return replace(
        signal,
        area=area,
        primary_class=primary_class,
        secondary_tags=tags,
        severity=severity,
        confidence=confidence,
        rationale=rationale,
        suggested_destination=destination,
        manual_triage=manual_triage,
    )


def _check_primary_class(signal: NormalizedSignal) -> PrimaryClass:
    source = _string_value(signal.raw_metadata.get("source")).casefold()
    name = _string_value(signal.raw_metadata.get("name")).casefold()
    if "validation" in source or "validation" in name or "ai-context-check" in name:
        return "validation_failure"
    return "ci_failure"


def _requires_manual_triage(primary_class: PrimaryClass, confidence: float) -> bool:
    return primary_class in PROMOTABLE_CLASSES and confidence < LOW_CONFIDENCE_THRESHOLD


def _miss_destination(signal: NormalizedSignal) -> Destination:
    if _has_mechanical_evidence(signal):
        return "test_or_linter"
    return "agents_check"


def _has_mechanical_evidence(signal: NormalizedSignal) -> bool:
    evidence_text = " ".join([signal.body, signal.rationale, *signal.evidence_ids])
    return MECHANICAL_RE.search(evidence_text) is not None


def _severity_for(signal: NormalizedSignal, primary_class: PrimaryClass) -> str:
    if CRITICAL_RE.search(signal.body):
        return "critical"
    if HIGH_RE.search(signal.body):
        return "high"
    if primary_class in {"miss", "ci_failure", "validation_failure", "post_merge_fix"}:
        return "medium"
    return "low"


def _source_tags(signal: NormalizedSignal) -> list[str]:
    source = " ".join(
        filter(
            None,
            [
                _string_value(signal.raw_metadata.get("source")),
                _string_value(signal.raw_metadata.get("provider")),
                _string_value(signal.raw_metadata.get("name")),
                signal.author,
            ],
        )
    ).casefold()
    if signal.kind == "check":
        if "validation" in source or "ai-context-check" in source:
            return ["validation"]
        return ["ci"]
    if "codex" in source:
        return ["codex_review"]
    if "builderbot" in source:
        return ["builderbot"]
    if signal.kind in FEEDBACK_KINDS:
        return ["human_review"]
    return []


def _check_failure_reason(primary_class: PrimaryClass) -> str:
    if primary_class == "validation_failure":
        return "failed validation signal"
    return "failed CI signal"


def _area_for_signal(signal: NormalizedSignal) -> str:
    raw_area = _string_value(signal.raw_metadata.get("area"))
    if raw_area:
        return raw_area
    path = signal.path or _string_value(signal.raw_metadata.get("path"))
    if path.startswith("app/"):
        return "app"
    if path.startswith("server/"):
        return "server"
    if path.startswith("firmware/"):
        return "firmware"
    if path.startswith("web/"):
        return "web"
    if path.startswith("core/"):
        return "core"
    if path.startswith("docs/"):
        return "docs"
    if path.startswith("automation/"):
        return "automation"
    return "repo-wide"


def _append_reason(existing: str, addition: str) -> str:
    return f"{existing}; {addition}" if existing else addition


def _correlate_feedback(
    feedback: NormalizedSignal,
    index: SignalIndex,
) -> Correlation:
    score = 0.0
    reasons: list[str] = []
    evidence_ids: list[str] = []

    score += _add_path_correlation(feedback, index, reasons, evidence_ids)
    score += _add_later_commit_correlation(feedback, index, reasons, evidence_ids)
    score += _add_fixed_reply_correlation(feedback, index, reasons, evidence_ids)
    score += _add_thread_resolution_correlation(feedback, reasons)
    score += _add_new_head_correlation(feedback, index, reasons, evidence_ids)
    score += _add_failed_check_correlation(feedback, index, reasons, evidence_ids)

    if feedback.kind == "bot_review" and feedback.body:
        score += 0.1
        reasons.append("bot review finding is an explicit non-human quality signal")

    confidence = min(round(score, 2), 0.95)
    if not reasons:
        reasons.append("no deterministic follow-up evidence found")
    return Correlation(
        likely_miss=confidence >= LIKELY_MISS_THRESHOLD,
        confidence=confidence,
        reasons=reasons,
        evidence_ids=_dedupe_preserving_order(evidence_ids),
    )


def _add_path_correlation(
    feedback: NormalizedSignal,
    index: SignalIndex,
    reasons: list[str],
    evidence_ids: list[str],
) -> float:
    if not feedback.path:
        return 0.0

    path_matches = [
        signal
        for signal in index.changes_by_path.get(feedback.path, [])
        if signal.pr_number == feedback.pr_number
    ]
    if not path_matches:
        return 0.0

    evidence_ids.extend(signal.source_id for signal in path_matches[:3])
    reasons.append(f"feedback path `{feedback.path}` matches harvested changed-file evidence")
    score = 0.2
    if feedback.line is not None and any(
        _line_inside_hunk(feedback.line, signal) for signal in path_matches
    ):
        score += 0.1
        reasons.append("feedback line falls inside a harvested diff hunk")
    return score


def _add_later_commit_correlation(
    feedback: NormalizedSignal,
    index: SignalIndex,
    reasons: list[str],
    evidence_ids: list[str],
) -> float:
    tied_commits = [
        signal for signal in index.by_kind.get("commit", [])
        if signal.pr_number == feedback.pr_number
        and signal.kind == "commit"
        and _is_after(signal.created_at, feedback.created_at)
        and _commit_appears_tied_to_feedback(feedback, signal)
    ]
    if not tied_commits:
        return 0.0

    evidence_ids.extend(signal.source_id for signal in tied_commits[:3])
    reasons.append("later commit message appears tied to the feedback")
    return 0.25


def _add_fixed_reply_correlation(
    feedback: NormalizedSignal,
    index: SignalIndex,
    reasons: list[str],
    evidence_ids: list[str],
) -> float:
    replies = [
        signal for signal in index.feedback
        if signal.kind in FEEDBACK_KINDS
        and signal.pr_number == feedback.pr_number
        and signal.source_id != feedback.source_id
        and _is_after(signal.created_at, feedback.created_at)
        and _same_feedback_thread(feedback, signal)
        and FIXED_REPLY_RE.search(signal.body)
    ]
    if not replies:
        return 0.0

    evidence_ids.extend(signal.source_id for signal in replies[:3])
    reasons.append("later reply text indicates the feedback was addressed")
    return 0.25


def _add_thread_resolution_correlation(
    feedback: NormalizedSignal,
    reasons: list[str],
) -> float:
    thread_resolved = (
        feedback.raw_metadata.get("thread_resolved") is True
        or feedback.raw_metadata.get("resolved") is True
    )
    if thread_resolved:
        reasons.append("review thread metadata indicates resolution")
        return 0.2
    return 0.0


def _add_new_head_correlation(
    feedback: NormalizedSignal,
    index: SignalIndex,
    reasons: list[str],
    evidence_ids: list[str],
) -> float:
    reviewed_head_sha = _string_value(feedback.raw_metadata.get("reviewed_head_sha"))
    if not reviewed_head_sha:
        return 0.0

    for signal in index.pr_metadata_by_pr.get(feedback.pr_number, []):
        final_head_sha = _string_value(_nested_metadata(signal, "shas", "head"))
        if final_head_sha and final_head_sha != reviewed_head_sha:
            evidence_ids.append(signal.source_id)
            reasons.append("feedback reviewed an earlier head than the final PR head")
            return 0.2
    return 0.0


def _add_failed_check_correlation(
    feedback: NormalizedSignal,
    index: SignalIndex,
    reasons: list[str],
    evidence_ids: list[str],
) -> float:
    later_checks = [
        signal for signal in index.by_kind.get("check", [])
        if signal.pr_number == feedback.pr_number
        and signal.kind == "check"
        and _is_after(signal.created_at, feedback.created_at)
    ]
    if not later_checks or not _feedback_has_quality_language(feedback):
        return 0.0

    evidence_ids.extend(signal.source_id for signal in later_checks[:3])
    labels = ", ".join(_check_evidence_label(signal) for signal in later_checks[:3])
    reasons.append(f"failed CI/check signal exists after the feedback timestamp: {labels}")
    return 0.15


def _commit_appears_tied_to_feedback(
    feedback: NormalizedSignal,
    commit: NormalizedSignal,
) -> bool:
    if not COMMIT_FIX_RE.search(commit.body):
        return False

    feedback_tokens = _correlation_tokens(" ".join([feedback.body, feedback.path or ""]))
    commit_tokens = _correlation_tokens(commit.body)
    if feedback_tokens.intersection(commit_tokens):
        return True

    if feedback.path:
        path_tokens = _correlation_tokens(feedback.path.replace("/", " "))
        if path_tokens.intersection(commit_tokens):
            return True

    return feedback.kind == "bot_review" and _feedback_has_quality_language(feedback)


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


def _is_fixed_reply_acknowledgement(signal: NormalizedSignal) -> bool:
    if signal.kind not in FEEDBACK_KINDS or not FIXED_REPLY_RE.search(signal.body):
        return False
    if NEW_FINDING_AFTER_ACK_RE.search(signal.body):
        return False
    return bool(
        _string_value(signal.raw_metadata.get("reply_to_source_id"))
        or _string_value(signal.raw_metadata.get("in_reply_to_id"))
    )


def _feedback_has_quality_language(feedback: NormalizedSignal) -> bool:
    return (
        feedback.kind == "bot_review"
        or MISS_RE.search(feedback.body) is not None
        or MECHANICAL_RE.search(feedback.body) is not None
    )


def _can_promote_correlated_miss(feedback: NormalizedSignal) -> bool:
    return _feedback_has_quality_language(feedback) or not matches_exclusion_pattern(feedback)


def _check_evidence_label(signal: NormalizedSignal) -> str:
    for key in ("name", "source", "provider"):
        value = _string_value(signal.raw_metadata.get(key)).strip()
        if value:
            return value

    body = " ".join(signal.body.split())
    if body:
        return body[:80]
    return signal.source_id


def _correlation_tokens(text: str) -> set[str]:
    return {
        _normalize_token(token)
        for token in TOKEN_RE.findall(text.casefold())
        if token not in COMMIT_TIE_STOP_WORDS
    }


def _normalize_token(token: str) -> str:
    if token.startswith("valid"):
        return "valid"
    if token.startswith("cover"):
        return "cover"
    if token.endswith("ing") and len(token) > 5:
        return token[:-3]
    if token.endswith("ed") and len(token) > 4:
        return token[:-2]
    if token.endswith("s") and len(token) > 4:
        return token[:-1]
    return token


def _line_inside_hunk(line: int, signal: NormalizedSignal) -> bool:
    if signal.kind != "diff_hunk":
        return False
    new_start = signal.raw_metadata.get("new_start")
    new_count = signal.raw_metadata.get("new_count")
    if not isinstance(new_start, int) or not isinstance(new_count, int) or new_count <= 0:
        return False
    return new_start <= line <= new_start + new_count - 1


def _is_after(timestamp: str, reference: str) -> bool:
    parsed_timestamp = _parse_timestamp(timestamp)
    parsed_reference = _parse_timestamp(reference)
    if parsed_timestamp is None or parsed_reference is None:
        return False
    return parsed_timestamp > parsed_reference


def _parse_timestamp(timestamp: str) -> datetime | None:
    if not timestamp:
        return None
    try:
        return datetime.fromisoformat(timestamp.replace("Z", "+00:00"))
    except ValueError:
        return None


def _nested_metadata(signal: NormalizedSignal, *keys: str) -> object:
    value: object = signal.raw_metadata
    for key in keys:
        if not isinstance(value, dict):
            return None
        value = value.get(key)
    return value


def _string_value(value: object) -> str:
    return value if isinstance(value, str) else ""


def _dedupe_preserving_order(values: list[str]) -> list[str]:
    deduped: list[str] = []
    seen: set[str] = set()
    for value in values:
        if value in seen:
            continue
        seen.add(value)
        deduped.append(value)
    return deduped
