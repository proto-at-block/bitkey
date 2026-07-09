"""Stage 2: normalize (BKW-58 — NORMALIZE-ONLY; no store).

Per the stateless architecture (BKW-64), normalization turns harvested signals into a stable
in-memory shape for downstream classification, clustering, and replay. It does not write records,
checkpoints, dedup stores, or harvest-versioned tables.
"""

from __future__ import annotations

from copy import deepcopy

from ..config import RunConfig
from ..models import NormalizedSignal, RawSignal


def normalize(cfg: RunConfig, signals: list[RawSignal]) -> list[NormalizedSignal]:
    """Normalize harvested signals in memory."""
    return [_normalize_signal(cfg, signal) for signal in signals]


def _normalize_signal(cfg: RunConfig, signal: RawSignal) -> NormalizedSignal:
    return NormalizedSignal(
        raw=signal,
        kind=signal.kind,
        source=_signal_source(signal),
        source_id=signal.source_id,
        source_url=signal.source_url,
        repo=signal.repo,
        pr_number=signal.pr_number,
        captured_at=signal.captured_at,
        harvest_version=cfg.harvest_version,
        body=signal.body,
        raw_metadata=deepcopy(signal.raw),
        author=signal.author,
        author_association=signal.author_association,
        created_at=signal.created_at,
        path=signal.path,
        line=signal.line,
        is_bot=signal.is_bot,
    )


def _signal_source(signal: RawSignal) -> str:
    raw_source = signal.raw.get("source")
    if isinstance(raw_source, str) and raw_source:
        return raw_source
    provider = signal.raw.get("provider")
    if isinstance(provider, str) and provider:
        return provider
    return signal.kind
