"""Injectable read-only boundary over the working repository.

Reality-check preflight and repo-context prompts must verify plans against the actual repo
(paths, runners, existing guidance) instead of trusting plan text. Tests inject a fake via
`cfg.extra["repo_reality"]`, mirroring how the LLM client is injected.
"""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path, PurePosixPath
import re
import subprocess

CHECKS_DIR = ".agents/checks"
SKILLS_DIR = ".ai/skills"
_SCOPED_AGENTS_MD = {
    "app": "app/.ai/AGENTS.md",
    "server": "server/.ai/AGENTS.md",
    "firmware": "firmware/.ai/AGENTS.md",
}
_ROOT_AGENTS_MD = ".ai/AGENTS.md"
_SHA_RE = re.compile(r"[0-9a-fA-F]{7,64}")
_FRONTMATTER_FIELD_RE = re.compile(r"(?m)^(name|description)\s*:\s*(.+?)\s*$")


@dataclass(frozen=True)
class CheckSummary:
    """Name and description of one existing `.agents/checks` guardrail."""

    name: str
    description: str
    path: str


class RepoReality:
    """Read-only checks against the real repository tree and git history."""

    def __init__(self, repo_root: Path | str) -> None:
        self._root = Path(repo_root)

    @property
    def root(self) -> Path:
        return self._root

    def file_exists(self, repo_path: str) -> bool:
        resolved = self._resolve(repo_path)
        return resolved is not None and resolved.is_file()

    def dir_exists(self, repo_path: str) -> bool:
        resolved = self._resolve(repo_path)
        return resolved is not None and resolved.is_dir()

    def parent_dir_exists(self, repo_path: str) -> bool:
        candidate = _safe_relative_path(repo_path)
        if candidate is None:
            return False
        parent = candidate.parent.as_posix()
        if parent == ".":
            return self._root.is_dir()
        return (self._root / parent).is_dir()

    def read_text(self, repo_path: str) -> str | None:
        """Return file content, or None when the path is missing or unreadable."""
        resolved = self._resolve(repo_path)
        if resolved is None or not resolved.is_file():
            return None
        try:
            return resolved.read_text(encoding="utf-8", errors="replace")
        except OSError:
            return None

    def references_in_repo(self, literal: str, *, max_hits: int = 1) -> int:
        """Count tracked files containing the literal string, capped at max_hits."""
        if not literal.strip() or max_hits <= 0:
            return 0
        try:
            completed = subprocess.run(
                [
                    "git",
                    "-C",
                    str(self._root),
                    "grep",
                    "-I",
                    "-l",
                    "-F",
                    "--max-count=1",
                    "--",
                    literal,
                ],
                check=False,
                capture_output=True,
                text=True,
            )
        except OSError:
            return 0
        if completed.returncode != 0:
            return 0
        hits = [line for line in completed.stdout.splitlines() if line.strip()]
        return min(len(hits), max_hits)

    def commit_exists(self, sha: str) -> bool:
        if not _SHA_RE.fullmatch(sha or ""):
            return False
        try:
            completed = subprocess.run(
                ["git", "-C", str(self._root), "cat-file", "-e", f"{sha}^{{commit}}"],
                check=False,
                capture_output=True,
                text=True,
            )
        except OSError:
            return False
        return completed.returncode == 0

    def existing_checks(self) -> tuple[CheckSummary, ...]:
        """Existing `.agents/checks/*.md` guardrails with frontmatter name/description."""
        checks_dir = self._root / CHECKS_DIR
        if not checks_dir.is_dir():
            return ()
        summaries: list[CheckSummary] = []
        for path in sorted(checks_dir.glob("*.md")):
            content = self.read_text(f"{CHECKS_DIR}/{path.name}") or ""
            fields = dict(_FRONTMATTER_FIELD_RE.findall(_frontmatter(content)))
            summaries.append(
                CheckSummary(
                    name=fields.get("name", path.stem),
                    description=fields.get("description", ""),
                    path=f"{CHECKS_DIR}/{path.name}",
                )
            )
        return tuple(summaries)

    def existing_skills(self) -> tuple[str, ...]:
        skills_dir = self._root / SKILLS_DIR
        if not skills_dir.is_dir():
            return ()
        return tuple(sorted(path.name for path in skills_dir.iterdir() if path.is_dir()))

    def scoped_agents_md_path(self, area: str) -> str:
        """Return the narrowest `.ai/AGENTS.md` source covering an area."""
        return _SCOPED_AGENTS_MD.get(area, _ROOT_AGENTS_MD)

    def _resolve(self, repo_path: str) -> Path | None:
        candidate = _safe_relative_path(repo_path)
        if candidate is None:
            return None
        return self._root / candidate.as_posix()


def _safe_relative_path(repo_path: str) -> PurePosixPath | None:
    if not repo_path or repo_path != repo_path.strip() or "\\" in repo_path:
        return None
    candidate = PurePosixPath(repo_path)
    if candidate.is_absolute() or not candidate.parts or ".." in candidate.parts:
        return None
    return candidate


def _frontmatter(content: str) -> str:
    text = content.lstrip()
    if not text.startswith("---"):
        return ""
    parts = text.split("---", maxsplit=2)
    if len(parts) < 3:
        return ""
    return parts[1]
