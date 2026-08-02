"""In-memory RepoReality fake for gate/preflight tests."""

from __future__ import annotations

from pathlib import PurePosixPath

from feedback_loop.repo_reality import CheckSummary

_SCOPED_AGENTS_MD = {
    "app": "app/.ai/AGENTS.md",
    "server": "server/.ai/AGENTS.md",
    "firmware": "firmware/.ai/AGENTS.md",
}


class FakeRepoReality:
    """Dict-backed stand-in for feedback_loop.repo_reality.RepoReality."""

    def __init__(
        self,
        *,
        files: dict[str, str] | None = None,
        dirs: set[str] | None = None,
        references: dict[str, int] | None = None,
        commits: set[str] | None = None,
        checks: tuple[CheckSummary, ...] = (),
        skills: tuple[str, ...] = (),
    ) -> None:
        self.files = dict(files or {})
        self.references = dict(references or {})
        self.commits = set(commits or set())
        self.checks = checks
        self.skills = skills
        self.dirs: set[str] = set(dirs or set())
        for path in [*self.files, *list(self.dirs)]:
            parent = PurePosixPath(path).parent
            while parent.as_posix() != ".":
                self.dirs.add(parent.as_posix())
                parent = parent.parent

    def file_exists(self, repo_path: str) -> bool:
        return repo_path in self.files

    def dir_exists(self, repo_path: str) -> bool:
        return repo_path.rstrip("/") in self.dirs

    def parent_dir_exists(self, repo_path: str) -> bool:
        parent = PurePosixPath(repo_path).parent.as_posix()
        return parent == "." or parent in self.dirs

    def read_text(self, repo_path: str) -> str | None:
        return self.files.get(repo_path)

    def references_in_repo(self, literal: str, *, max_hits: int = 1) -> int:
        return min(self.references.get(literal, 0), max_hits)

    def commit_exists(self, sha: str) -> bool:
        return sha in self.commits

    def existing_checks(self) -> tuple[CheckSummary, ...]:
        return self.checks

    def existing_skills(self) -> tuple[str, ...]:
        return self.skills

    def scoped_agents_md_path(self, area: str) -> str:
        return _SCOPED_AGENTS_MD.get(area, ".ai/AGENTS.md")
