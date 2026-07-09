"""Stage 4b: human triage report for classified clusters (BKW-53)."""

from __future__ import annotations

from dataclasses import dataclass

from ..models import Cluster, NormalizedSignal

PROMOTION_FREQUENCY_MIN = {"critical": 1, "high": 2, "medium": 3, "low": 5}


@dataclass
class TriageReport:
    """Human-readable markdown plus machine-readable cluster summary."""

    markdown: str
    summary: list[dict]


def build_triage_report(clusters: list[Cluster]) -> TriageReport:
    """Build a reviewable report before any guardrail proposal is generated."""
    ordered = sorted(clusters, key=lambda item: (-item.rank, item.theme))
    summary = [_cluster_summary(cluster) for cluster in ordered]
    return TriageReport(markdown=_render_markdown(ordered, summary), summary=summary)


def _render_markdown(clusters: list[Cluster], summary: list[dict]) -> str:
    lines = [
        "# Feedback Loop Triage Report",
        "",
        "Review each cluster before any guardrail proposal is generated.",
        "",
        "Decision options: `promote`, `gather_more_evidence`, `ignore`, `convert_to_mechanical_check`.",
        "",
    ]
    if not clusters:
        lines.extend(["No classified clusters found.", ""])
        return "\n".join(lines)

    for index, cluster in enumerate(clusters, start=1):
        item = summary[index - 1]
        lines.extend(
            [
                f"## {index}. {cluster.theme}",
                "",
                f"- Decision: `{item['decision']}`",
                f"- Severity: `{cluster.severity or 'unknown'}`",
                f"- Frequency: `{cluster.frequency}` distinct PR(s)",
                f"- Rank: `{cluster.rank}`",
                f"- Confidence: `{item['confidence']}`",
                f"- Suggested destination: `{cluster.suggested_destination or 'none'}`",
                f"- Summary: {cluster.summary or 'No summary available.'}",
                "",
                "Evidence:",
                *_evidence_lines(cluster),
                "",
                "Representative examples:",
                *_example_lines(cluster),
                "",
                "Open questions:",
                *_open_question_lines(item["open_questions"]),
                "",
            ]
        )
    return "\n".join(lines)


def _cluster_summary(cluster: Cluster) -> dict:
    signals = cluster.signals
    return {
        "theme": cluster.theme,
        "decision": _decision_for(cluster),
        "severity": cluster.severity,
        "frequency": cluster.frequency,
        "rank": cluster.rank,
        "confidence": _average_confidence(signals),
        "suggested_destination": cluster.suggested_destination,
        "excluded_only": cluster.excluded_only,
        "manual_triage": _has_manual_triage(cluster),
        "source_urls": list(cluster.source_urls),
        "source_ids": [signal.source_id for signal in signals],
        "open_questions": _open_questions(cluster),
    }


def _decision_for(cluster: Cluster) -> str:
    if cluster.excluded_only:
        return "ignore"
    if _has_manual_triage(cluster):
        return "gather_more_evidence"
    if not _meets_promotion_threshold(cluster):
        return "gather_more_evidence"
    if cluster.suggested_destination == "test_or_linter":
        return "convert_to_mechanical_check"
    if cluster.rank > 0:
        return "promote"
    return "gather_more_evidence"


def _open_questions(cluster: Cluster) -> list[str]:
    questions: list[str] = []
    if cluster.excluded_only:
        questions.append("Confirm excluded-only feedback should remain context or be ignored.")
    if _has_manual_triage(cluster):
        questions.append("Resolve low-confidence/manual-triage signals before promotion.")
    if not _meets_promotion_threshold(cluster):
        threshold = _promotion_threshold(cluster)
        if cluster.severity == "low" and cluster.suggested_destination != "test_or_linter":
            questions.append("Low-severity clusters must be mechanically enforceable before promotion.")
        questions.append(
            f"{cluster.severity or 'unknown'} clusters need at least {threshold} distinct PR(s) before promotion."
        )
    if not cluster.source_urls:
        questions.append("No source URLs are available; verify evidence before acting.")
    return questions or ["No open questions identified."]


def _meets_promotion_threshold(cluster: Cluster) -> bool:
    threshold = _promotion_threshold(cluster)
    if cluster.frequency < threshold:
        return False
    if cluster.severity == "low" and cluster.suggested_destination != "test_or_linter":
        return False
    return True


def _promotion_threshold(cluster: Cluster) -> int:
    return PROMOTION_FREQUENCY_MIN.get(cluster.severity or "low", 5)


def _has_manual_triage(cluster: Cluster) -> bool:
    return any(signal.manual_triage for signal in cluster.signals)


def _average_confidence(signals: list[NormalizedSignal]) -> float:
    confidences = [signal.confidence for signal in signals if signal.confidence is not None]
    if not confidences:
        return 0.0
    return round(sum(confidences) / len(confidences), 2)


def _evidence_lines(cluster: Cluster) -> list[str]:
    if not cluster.source_urls:
        return ["- No source links available."]
    return [f"- {url}" for url in cluster.source_urls]


def _example_lines(cluster: Cluster) -> list[str]:
    if not cluster.representative_examples:
        return ["- No representative examples available."]
    return [f"- {example}" for example in cluster.representative_examples]


def _open_question_lines(open_questions: list[str]) -> list[str]:
    return [f"- {question}" for question in open_questions]
