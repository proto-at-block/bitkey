"""Bounded fan-out helpers for stage-internal parallelism.

`max_workers <= 1` (the default with no env set) takes a literal sequential path, so existing
behavior — including FakeLlmClient call ordering in tests — is preserved unless a run opts in via
FEEDBACK_LOOP_LLM_CONCURRENCY / FEEDBACK_LOOP_HARVEST_CONCURRENCY or cfg.extra.
"""

from __future__ import annotations

from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass
import os
from typing import Any, Callable, Sequence

LLM_CONCURRENCY_ENV = "FEEDBACK_LOOP_LLM_CONCURRENCY"
HARVEST_CONCURRENCY_ENV = "FEEDBACK_LOOP_HARVEST_CONCURRENCY"


@dataclass(frozen=True)
class Slot:
    """One fan-out result: a value or the exception the worker raised."""

    value: Any = None
    error: Exception | None = None

    def unwrap(self) -> Any:
        if self.error is not None:
            raise self.error
        return self.value


def parallel_map_indexed(
    items: Sequence[Any],
    fn: Callable[[Any], Any],
    *,
    max_workers: int,
    warm_first: bool = True,
) -> list[Slot]:
    """Map fn over items, returning Slots in submission order regardless of completion order.

    Exceptions are captured per slot and re-raised by unwrap() at merge time, so callers keep
    their existing per-item try/except semantics. With warm_first, item 0 runs synchronously
    before the pool opens — one call populates the stage's prompt-cache prefix instead of the
    whole fan-out racing to write it.
    """
    work = list(items)
    if max_workers <= 1 or len(work) <= 1:
        return [_call(fn, item) for item in work]

    slots: list[Slot | None] = [None] * len(work)
    start = 0
    if warm_first:
        slots[0] = _call(fn, work[0])
        start = 1
    with ThreadPoolExecutor(max_workers=max_workers) as pool:
        futures = {pool.submit(_call, fn, work[index]): index for index in range(start, len(work))}
        for future, index in futures.items():
            slots[index] = future.result()
    return [slot for slot in slots if slot is not None]


def _call(fn: Callable[[Any], Any], item: Any) -> Slot:
    try:
        return Slot(value=fn(item))
    except Exception as err:  # noqa: BLE001 — captured for ordered re-raise via unwrap()
        return Slot(error=err)


def llm_max_workers(cfg: Any = None) -> int:
    """Max parallel LLM calls; cfg.extra["llm_concurrency"] overrides the env knob."""
    return _resolve(cfg, "llm_concurrency", LLM_CONCURRENCY_ENV)


def harvest_max_workers(cfg: Any = None) -> int:
    """Max parallel per-PR harvests; keep small (gh secondary rate limits)."""
    return _resolve(cfg, "harvest_concurrency", HARVEST_CONCURRENCY_ENV)


def _resolve(cfg: Any, extra_key: str, env_name: str) -> int:
    if cfg is not None:
        value = getattr(cfg, "extra", {}).get(extra_key)
        if value is not None:
            try:
                return max(1, int(value))
            except (TypeError, ValueError):
                return 1
    raw = os.environ.get(env_name, "").strip()
    if raw:
        try:
            return max(1, int(raw))
        except ValueError:
            return 1
    return 1
