"""Shared route metadata helpers for generated feedback-loop handoffs."""

from __future__ import annotations

import hashlib
from pathlib import PurePosixPath
import re
from typing import TYPE_CHECKING, Any

if TYPE_CHECKING:
    from .models import Proposal

DANGLING_TITLE_WORDS = frozenset(
    {"a", "an", "and", "at", "for", "in", "of", "on", "or", "the", "to", "with"}
)


def sanitize_handoff_title(value: str, *, max_chars: int = 72) -> str:
    """Return a short markdown-free title without truncating on dangling words."""
    text = re.sub(r"\[([^\]]+)\]\([^)]+\)", r"\1", value)
    text = text.replace("`", "")
    text = re.sub(r"[#*_>\[\]{}]", " ", text)
    text = re.sub(r"\s+", " ", text).strip(" -:.,")
    if not text:
        return "Focused feedback-loop guardrail"
    if len(text) <= max_chars:
        return text

    truncated = text[:max_chars].rstrip()
    if " " in truncated:
        truncated = truncated.rsplit(" ", maxsplit=1)[0]
    truncated = truncated.strip(" -:.,")
    while truncated and truncated.casefold().split()[-1] in DANGLING_TITLE_WORDS:
        if " " not in truncated:
            break
        truncated = truncated.rsplit(" ", maxsplit=1)[0].strip(" -:.,")
    return truncated or text[:max_chars].strip(" -:.,")


def change_set_id(route_id: str, file_changes: list[Any] | tuple[Any, ...]) -> str:
    """Return a deterministic route/file-content change-set id."""
    raw = "|".join([route_id or "route", *sorted(_change_set_components(file_changes))])
    digest = hashlib.sha256(raw.encode("utf-8")).hexdigest()[:16]
    return f"change-set:{digest}"


def proposal_change_set_id(proposal: "Proposal") -> str:
    """Return a proposal's stored change-set id or compute the fallback."""
    if proposal.change_set_id:
        return proposal.change_set_id
    return change_set_id(
        proposal.route_id or proposal.destination,
        list(proposal.file_changes),
    )


def _change_set_components(file_changes: list[Any] | tuple[Any, ...]) -> list[str]:
    components: list[str] = []
    for change in file_changes:
        if isinstance(change, str):
            components.append(f"path:{PurePosixPath(change).as_posix()}")
            continue
        if isinstance(change, dict):
            path = str(change.get("path", ""))
            mode = str(change.get("mode", ""))
            content = str(change.get("content", ""))
        else:
            path = str(getattr(change, "path", ""))
            mode = str(getattr(change, "mode", ""))
            content = str(getattr(change, "content", ""))
        content_digest = hashlib.sha256(content.encode("utf-8")).hexdigest()[:16]
        components.append(
            f"file:{PurePosixPath(path).as_posix()}:{mode}:{content_digest}"
        )
    return components
