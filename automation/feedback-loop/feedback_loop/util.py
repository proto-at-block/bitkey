"""Shared helpers used across pipeline stages.

Single home for small utilities (dedupe, severity weights, the promotion frequency matrix, area
mapping, timestamps, resolution counts) so every stage applies the same definitions.
"""

from __future__ import annotations

from datetime import datetime, timezone
import os
import re
from typing import TYPE_CHECKING, Iterable

if TYPE_CHECKING:
    from .models import Cluster

SEVERITY_WEIGHT = {"critical": 8, "high": 4, "medium": 2, "low": 1}

# Promotion frequency matrix (docs/docs/automation/feedback-loop-taxonomy.md): minimum distinct
# PRs before a theme may be promoted. Low severity additionally requires mechanical enforcement.
PROMOTION_FREQUENCY_MIN = {"critical": 1, "high": 2, "medium": 3, "low": 5}


def promotion_threshold(severity: str | None) -> int:
    """Minimum distinct PRs before a theme of this severity may promote (the taxonomy matrix)."""
    return PROMOTION_FREQUENCY_MIN.get(severity or "low", PROMOTION_FREQUENCY_MIN["low"])


def env_int(name: str, default: int, *, minimum: int = 1) -> int:
    """Read an int env var, falling back to default if unset, unparseable, or below `minimum`."""
    raw = os.environ.get(name, "").strip()
    if not raw:
        return default
    try:
        value = int(raw)
    except ValueError:
        return default
    return value if value >= minimum else default


def env_float(name: str, default: float, *, minimum: float, maximum: float) -> float:
    """Read a float env var, falling back to default unless it parses within `(minimum, maximum]`."""
    raw = os.environ.get(name, "").strip()
    if not raw:
        return default
    try:
        value = float(raw)
    except ValueError:
        return default
    return value if minimum < value <= maximum else default

RESOLUTION_STATES = (
    "unresolved",
    "resolved_without_durable_coverage",
    "resolved_with_durable_coverage",
)

GITHUB_PR_URL_RE = re.compile(
    r"https://github\.com/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+/pull/(\d+)"
    r"(?:[A-Za-z0-9_./?&=#:%+-]*)?"
)

_AREA_PREFIXES: tuple[tuple[str, str], ...] = (
    ("app/", "app"),
    ("server/", "server"),
    ("firmware/", "firmware"),
    ("web/", "web"),
    ("core/", "core"),
    ("docs/", "docs"),
    ("automation/", "automation"),
)


def dedupe(values: Iterable[str]) -> list[str]:
    """Dedupe strings preserving first-occurrence order."""
    deduped: list[str] = []
    seen: set[str] = set()
    for value in values:
        if value in seen:
            continue
        seen.add(value)
        deduped.append(value)
    return deduped


def highest_severity(severities: Iterable[str]) -> str:
    """Return the highest-weight severity, defaulting unknowns to low weight."""
    normalized = [
        severity if severity in SEVERITY_WEIGHT else "low"
        for severity in (value or "low" for value in severities)
    ]
    return max(normalized or ["low"], key=lambda item: SEVERITY_WEIGHT[item])


def area_for_path(path: str, *, fallback: str = "repo-wide") -> str:
    """Map a repo path to its top-level area tag."""
    for prefix, area in _AREA_PREFIXES:
        if path.startswith(prefix):
            return area
    return fallback


def resolution_counts(signals: Iterable[object]) -> dict[str, int]:
    """Count signals by resolution state; signals without a resolution are unresolved."""
    counts = {state: 0 for state in RESOLUTION_STATES}
    for signal in signals:
        resolution = getattr(signal, "resolution", None)
        state = "unresolved" if resolution is None else str(resolution.state)
        counts[state] = counts.get(state, 0) + 1
    return counts


def is_review_only(cluster: "Cluster") -> bool:
    """True when every signal is an unexcluded review-only class (false_positive/not_actionable)."""
    from .models import REVIEW_ONLY_CLASSES

    return bool(cluster.signals) and all(
        signal.primary_class in REVIEW_ONLY_CLASSES and not signal.is_excluded
        for signal in cluster.signals
    )


def pr_numbers_from_urls(urls: Iterable[str]) -> tuple[int, ...]:
    """Extract distinct PR numbers from GitHub PR URLs, sorted ascending."""
    numbers: set[int] = set()
    for url in urls:
        for match in GITHUB_PR_URL_RE.finditer(url):
            numbers.add(int(match.group(1)))
    return tuple(sorted(numbers))


def parse_timestamp(timestamp: str) -> datetime | None:
    """Parse an ISO timestamp; naive values are assumed UTC."""
    if not timestamp:
        return None
    try:
        parsed = datetime.fromisoformat(timestamp.replace("Z", "+00:00"))
    except ValueError:
        return None
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=timezone.utc)
    return parsed.astimezone(timezone.utc)


def is_after(timestamp: str, reference: str) -> bool:
    """True when both timestamps parse and `timestamp` is strictly after `reference`."""
    parsed_timestamp = parse_timestamp(timestamp)
    parsed_reference = parse_timestamp(reference)
    if parsed_timestamp is None or parsed_reference is None:
        return False
    return parsed_timestamp > parsed_reference


def excerpt(value: str, max_chars: int) -> str:
    """Whitespace-normalized excerpt with an ellipsis when truncated."""
    text = " ".join(value.split())
    if len(text) <= max_chars:
        return text
    return f"{text[: max_chars - 3].rstrip()}..."
