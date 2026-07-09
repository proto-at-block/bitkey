"""Stage 4: cluster (BKW-76 theme clustering, BKW-53 triage report).

Groups classified signals into themes ranked by severity_weight x frequency (distinct PRs), applies
the thin-cluster threshold from the taxonomy doc (BKW-77), and produces the human triage report
(BKW-53). Excluded-only clusters (nit/product_decision/not_actionable) cannot proceed (BKW-83).
"""

from __future__ import annotations

import re

from ..config import RunConfig
from ..models import REVIEW_ONLY_CLASSES, Cluster, NormalizedSignal

ACTIONABLE_CLASSES = {"miss", "ci_failure", "validation_failure", "post_merge_fix"}
SEVERITY_WEIGHT = {"critical": 8, "high": 4, "medium": 2, "low": 1}
TOPIC_RE = re.compile(
    r"\b(validation|test|coverage|lint|build|ci|auth|keyset|recovery|mobile pay|logging|"
    r"telemetry|descriptor|backup|firmware|server|database|sql|api)\b",
    re.IGNORECASE,
)
TOKEN_RE = re.compile(r"[a-z0-9][a-z0-9_-]{2,}")
STOP_WORDS = {
    "this",
    "that",
    "with",
    "from",
    "have",
    "should",
    "could",
    "would",
    "please",
    "missing",
    "feedback",
    "comment",
    "review",
    "later",
    "commit",
    "actionable",
    "classifier",
    "deterministic",
    "evidence",
    "exists",
    "follow-up",
    "found",
}


def cluster(cfg: RunConfig, signals: list[NormalizedSignal]) -> list[Cluster]:
    """Cluster classified signals into stable themes and rank them."""
    grouped: dict[tuple[str, str, str, str], list[NormalizedSignal]] = {}
    for signal in signals:
        if not _clusterable(signal):
            continue
        grouped.setdefault(_cluster_key(signal), []).append(signal)

    clusters = [_build_cluster(key, group) for key, group in grouped.items()]
    return sorted(clusters, key=lambda item: (-item.rank, item.theme, item.frequency))


def merge_clusters(cfg: RunConfig, clusters: list[Cluster]) -> list[Cluster]:
    """Merge independently clustered PR results by theme for one final triage pass."""
    grouped: dict[str, list[NormalizedSignal]] = {}
    for cluster_item in clusters:
        grouped.setdefault(cluster_item.theme, []).extend(cluster_item.signals)

    merged = [
        _build_cluster(_cluster_key(signals[0]), signals)
        for signals in grouped.values()
        if signals
    ]
    return sorted(merged, key=lambda item: (-item.rank, item.theme, item.frequency))


def _clusterable(signal: NormalizedSignal) -> bool:
    if signal.primary_class in ACTIONABLE_CLASSES:
        return True
    if signal.primary_class in REVIEW_ONLY_CLASSES and not signal.is_excluded:
        return True
    return signal.is_excluded


def _cluster_key(signal: NormalizedSignal) -> tuple[str, str, str, str]:
    primary_class = signal.primary_class or "unclassified"
    area = signal.area or _area_from_signal(signal)
    if signal.primary_class in REVIEW_ONLY_CLASSES and not signal.is_excluded:
        return (primary_class, area, "review_only", _topic_for_signal(signal))
    destination = signal.suggested_destination or ("excluded" if signal.is_excluded else "manual_triage")
    return (primary_class, area, destination, _topic_for_signal(signal))


def _build_cluster(
    key: tuple[str, str, str, str],
    signals: list[NormalizedSignal],
) -> Cluster:
    primary_class, area, destination, topic = key
    ordered = sorted(signals, key=lambda item: (item.pr_number, item.source_id))
    severity = _highest_severity(ordered)
    frequency = len({signal.pr_number for signal in ordered})
    excluded_only = all(signal.is_excluded for signal in ordered)
    review_only = destination == "review_only"
    rank = (
        0.0
        if excluded_only or review_only
        else float(SEVERITY_WEIGHT.get(severity, 1) * frequency)
    )
    destination_value = None if destination in {"excluded", "manual_triage", "review_only"} else destination
    theme = f"{primary_class}:{area}:{topic}:{destination}"
    summary = (
        f"{frequency} PR(s), {len(ordered)} signal(s), {severity} severity: "
        f"{primary_class} in {area} about {topic}"
    )
    return Cluster(
        theme=theme,
        signals=ordered,
        area=area,
        severity=severity,
        frequency=frequency,
        rank=rank,
        suggested_destination=destination_value,
        summary=summary,
        representative_examples=_representative_examples(ordered),
        source_urls=_dedupe([signal.source_url for signal in ordered if signal.source_url]),
    )


def _highest_severity(signals: list[NormalizedSignal]) -> str:
    severities = (signal.severity or "low" for signal in signals)
    return max(severities, key=lambda item: SEVERITY_WEIGHT.get(item, 0))


def _topic_for_signal(signal: NormalizedSignal) -> str:
    text = " ".join([signal.path or "", signal.body, signal.rationale]).casefold()
    match = TOPIC_RE.search(text)
    if match:
        return match.group(1).replace(" ", "-")
    tokens = [token for token in TOKEN_RE.findall(text) if token not in STOP_WORDS]
    return "-".join(tokens[:3]) if tokens else "general"


def _area_from_signal(signal: NormalizedSignal) -> str:
    path = signal.path or ""
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


def _representative_examples(signals: list[NormalizedSignal]) -> list[str]:
    examples: list[str] = []
    for signal in signals[:3]:
        body = " ".join(signal.body.split())
        excerpt = body[:140] if body else signal.rationale[:140]
        examples.append(f"{signal.source_id}: {excerpt}")
    return examples


def _dedupe(values: list[str]) -> list[str]:
    deduped: list[str] = []
    seen: set[str] = set()
    for value in values:
        if value in seen:
            continue
        seen.add(value)
        deduped.append(value)
    return deduped
