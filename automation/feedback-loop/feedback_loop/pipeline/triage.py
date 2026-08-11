"""Human triage report over LLM-clustered themes.

Decisions are computed deterministically at clustering time (cluster.decision); this stage only
renders them. The audit view accounts for signals that never reach clustering: bot/process noise,
LLM-excluded classes, review-only classes, and classifier fallbacks.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Iterable

from ..models import REVIEW_ONLY_CLASSES, Cluster, NormalizedSignal
from ..util import PROMOTION_FREQUENCY_MIN, dedupe, resolution_counts

COMMENT_KINDS = frozenset({"issue_comment", "review_comment", "review", "bot_review"})
VISIBLE_DECISIONS = frozenset({"promote", "convert_to_mechanical_check", "gather_more_evidence"})


@dataclass
class TriageReport:
    """Human-readable markdown plus machine-readable cluster summary."""

    markdown: str
    summary: list[dict]
    comment_volume_summary: dict[str, int]


def build_triage_report(
    clusters: list[Cluster],
    *,
    audit_signals: Iterable[NormalizedSignal] = (),
    include_audit_only: bool = True,
) -> TriageReport:
    """Render clusters (and, in the full report, unclustered audit signals)."""
    audit = list(audit_signals)
    ordered = sorted(clusters, key=lambda item: (-item.rank, item.slug))
    visible = [cluster for cluster in ordered if cluster.decision in VISIBLE_DECISIONS]
    selected = ordered if include_audit_only else visible
    summary = [_cluster_summary(cluster) for cluster in selected]
    volume = _comment_volume_summary(ordered, visible, audit)
    return TriageReport(
        markdown=_render_markdown(
            selected,
            summary,
            volume,
            audit_signals=audit if include_audit_only else [],
            empty_message=(
                "No classified clusters found."
                if include_audit_only
                else "No actionable or manual-triage clusters found."
            ),
        ),
        summary=summary,
        comment_volume_summary=volume,
    )


def facts_only_report(
    signals: list[NormalizedSignal],
    noise_signals: list[NormalizedSignal],
    *,
    reason: str = "LLM client not configured — classification and clustering were skipped.",
) -> TriageReport:
    """Degraded inventory when no LLM client is available."""
    comments = [signal for signal in signals if signal.kind in COMMENT_KINDS]
    by_kind: dict[str, int] = {}
    for signal in signals:
        by_kind[signal.kind] = by_kind.get(signal.kind, 0) + 1
    lines = [
        "# Feedback Loop Facts-Only Report",
        "",
        reason,
        "",
        "Signal inventory:",
        *(f"- {kind}: `{count}`" for kind, count in sorted(by_kind.items())),
        f"- noise (excluded pre-classification): `{len(noise_signals)}`",
        "",
        f"Feedback comments awaiting classification: `{len(comments)}`",
        "",
    ]
    volume = {
        "total": len(comments) + len([s for s in noise_signals if s.kind in COMMENT_KINDS]),
        "visible": 0,
        "audit_only": len(comments),
        "acknowledgement_or_noise": len(
            [s for s in noise_signals if s.kind in COMMENT_KINDS]
        ),
        "already_covered": 0,
        "unresolved": len(comments),
        "resolved_without_durable_coverage": 0,
    }
    return TriageReport(markdown="\n".join(lines), summary=[], comment_volume_summary=volume)


def _render_markdown(
    clusters: list[Cluster],
    summary: list[dict],
    comment_volume_summary: dict[str, int],
    *,
    audit_signals: list[NormalizedSignal],
    empty_message: str,
) -> str:
    lines = [
        "# Feedback Loop Triage Report",
        "",
        "Review each cluster before any guardrail proposal is generated.",
        "",
        "Decision options: `promote`, `convert_to_mechanical_check`, `gather_more_evidence`, "
        "`already_covered`, `ignore`.",
        "",
        "Comment volume:",
        f"- Total: `{comment_volume_summary['total']}`",
        f"- Visible: `{comment_volume_summary['visible']}`",
        f"- Audit-only: `{comment_volume_summary['audit_only']}`",
        f"- Acknowledgement/noise: `{comment_volume_summary['acknowledgement_or_noise']}`",
        f"- Already covered: `{comment_volume_summary['already_covered']}`",
        f"- Unresolved: `{comment_volume_summary['unresolved']}`",
        f"- Resolved without durable coverage: `{comment_volume_summary['resolved_without_durable_coverage']}`",
        "",
    ]
    if not clusters:
        lines.extend([empty_message, ""])
    for index, cluster in enumerate(clusters, start=1):
        item = summary[index - 1]
        lines.extend(
            [
                f"## {index}. {cluster.title or cluster.slug}",
                "",
                f"- Slug: `{cluster.slug}`",
                f"- Decision: `{item['decision']}`",
                f"- Severity: `{cluster.severity or 'unknown'}`",
                f"- Frequency: `{cluster.frequency}` distinct PR(s)",
                f"- Rank: `{cluster.rank}`",
                f"- Suggested destination: `{cluster.suggested_destination or 'none'}`",
                f"- Matched memory: {cluster.matched_issue_url or '`new theme`'}",
                f"- Summary: {cluster.summary or 'No summary available.'}",
                "",
                "Evidence:",
                *_evidence_lines(cluster),
                "",
                "Representative examples:",
                *_example_lines(cluster),
                "",
                "Open questions:",
                *(f"- {question}" for question in item["open_questions"]),
                "",
            ]
        )
    if audit_signals:
        lines.extend(_audit_section(audit_signals))
    return "\n".join(lines)


def _audit_section(audit_signals: list[NormalizedSignal]) -> list[str]:
    by_class: dict[str, int] = {}
    for signal in audit_signals:
        key = signal.primary_class or "unclassified"
        if signal.is_excluded and signal.exclusion is not None:
            key = f"excluded:{signal.exclusion.reason}"
        by_class[key] = by_class.get(key, 0) + 1
    return [
        "## Audit-only signals (not clustered)",
        "",
        *(f"- {key}: `{count}`" for key, count in sorted(by_class.items())),
        "",
    ]


def _cluster_summary(cluster: Cluster) -> dict:
    signals = cluster.signals
    return {
        "slug": cluster.slug,
        "title": cluster.title,
        "decision": cluster.decision,
        "severity": cluster.severity,
        "frequency": cluster.frequency,
        "rank": cluster.rank,
        "confidence": _average_confidence(signals),
        "suggested_destination": cluster.suggested_destination,
        "matched_memory_key": cluster.matched_memory_key,
        "matched_issue_identifier": cluster.matched_issue_identifier,
        "matched_issue_url": cluster.matched_issue_url,
        "manual_triage": any(signal.manual_triage for signal in signals),
        "already_covered": cluster.already_covered,
        "resolution_counts": resolution_counts(signals),
        "coverage_paths": _coverage_paths(signals),
        "current_pr_numbers": list(cluster.current_pr_numbers),
        "merged_pr_numbers": list(cluster.merged_pr_numbers),
        "source_urls": list(cluster.source_urls),
        "source_ids": [signal.source_id for signal in signals],
        "rationale": cluster.rationale,
        "open_questions": _open_questions(cluster),
    }


def _open_questions(cluster: Cluster) -> list[str]:
    questions: list[str] = []
    if cluster.decision == "already_covered":
        questions.append("No proposal needed; same-PR evidence already added durable coverage.")
    if cluster.decision == "ignore":
        questions.append("No learning signals remain; confirm the exclusion is correct.")
    if any(signal.manual_triage for signal in cluster.signals):
        questions.append("Resolve low-confidence/manual-triage signals before promotion.")
    threshold = PROMOTION_FREQUENCY_MIN.get(cluster.severity or "low", 5)
    if cluster.decision == "gather_more_evidence" and cluster.frequency < threshold:
        questions.append(
            f"{cluster.severity or 'unknown'} clusters need at least {threshold} distinct PR(s) "
            "before promotion."
        )
    if cluster.severity == "low" and cluster.suggested_destination != "test_or_linter":
        questions.append("Low-severity clusters must be mechanically enforceable before promotion.")
    if not cluster.source_urls:
        questions.append("No source URLs are available; verify evidence before acting.")
    return questions or ["No open questions identified."]


def _average_confidence(signals: list[NormalizedSignal]) -> float:
    confidences = [signal.confidence for signal in signals if signal.confidence is not None]
    if not confidences:
        return 0.0
    return round(sum(confidences) / len(confidences), 2)


def _comment_volume_summary(
    all_clusters: list[Cluster],
    visible_clusters: list[Cluster],
    audit_signals: list[NormalizedSignal],
) -> dict[str, int]:
    cluster_comments = _comment_signals(all_clusters)
    visible_comments = _comment_signals(visible_clusters)
    audit_comments = [signal for signal in audit_signals if signal.kind in COMMENT_KINDS]
    visible_ids = {id(signal) for signal in visible_comments}
    all_comments = [*cluster_comments, *audit_comments]
    counts = resolution_counts(all_comments)
    return {
        "total": len(all_comments),
        "visible": len(visible_comments),
        "audit_only": len([signal for signal in all_comments if id(signal) not in visible_ids]),
        "acknowledgement_or_noise": len(
            [signal for signal in all_comments if _is_acknowledgement_or_noise(signal)]
        ),
        "already_covered": counts["resolved_with_durable_coverage"],
        "unresolved": counts["unresolved"],
        "resolved_without_durable_coverage": counts["resolved_without_durable_coverage"],
    }


def _comment_signals(clusters: list[Cluster]) -> list[NormalizedSignal]:
    return [
        signal
        for cluster in clusters
        for signal in cluster.signals
        if signal.kind in COMMENT_KINDS
    ]


def _is_acknowledgement_or_noise(signal: NormalizedSignal) -> bool:
    if signal.exclusion is not None:
        return True
    return signal.primary_class in REVIEW_ONLY_CLASSES


def _coverage_paths(signals: list[NormalizedSignal]) -> list[str]:
    paths: list[str] = []
    for signal in signals:
        if signal.resolution is not None:
            paths.extend(signal.resolution.coverage_paths)
    return dedupe(paths)


def _evidence_lines(cluster: Cluster) -> list[str]:
    if not cluster.source_urls:
        return ["- No source links available."]
    return [f"- {url}" for url in cluster.source_urls]


def _example_lines(cluster: Cluster) -> list[str]:
    if not cluster.representative_examples:
        return ["- No representative examples available."]
    return [f"- {example}" for example in cluster.representative_examples]
