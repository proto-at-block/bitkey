"""Owned read-only git boundary for replay diff reconstruction.

Mirrors the `gh` subprocess boundary in github.py: small surface, validated inputs, explicit
errors. Only read commands are issued.
"""

from __future__ import annotations

from pathlib import Path
from pathlib import PurePosixPath
import re
import shlex
import subprocess

MAX_REPLAY_DIFF_BYTES = 50_000
_SHA_RE = re.compile(r"[0-9a-fA-F]{7,64}")


class GitError(Exception):
    """Raised when a git command or its inputs are invalid."""


class GitClient:
    """Read-only git commands scoped to one repository root."""

    def __init__(self, repo_root: Path | str) -> None:
        self.repo_root = Path(repo_root)

    def commit_exists(self, sha: str) -> bool:
        if not _SHA_RE.fullmatch(sha or ""):
            return False
        try:
            completed = subprocess.run(
                ["git", "-C", str(self.repo_root), "cat-file", "-e", f"{sha}^{{commit}}"],
                check=False,
                capture_output=True,
                text=True,
            )
        except OSError:
            return False
        return completed.returncode == 0

    def diff_range(
        self,
        base: str,
        head: str,
        paths: tuple[str, ...] = (),
        *,
        max_bytes: int = MAX_REPLAY_DIFF_BYTES,
    ) -> str:
        """Return `git diff base...head -- paths`, truncated at max_bytes with a trailer."""
        for sha in (base, head):
            if not _SHA_RE.fullmatch(sha or ""):
                raise GitError(f"invalid commit sha: {sha!r}")
        for path in paths:
            _validate_diff_path(path)
        args = ["diff", "--no-color", f"{base}...{head}"]
        if paths:
            args.extend(["--", *paths])
        output = self._run_git(args)
        encoded = output.encode("utf-8")
        if len(encoded) <= max_bytes:
            return output
        truncated = encoded[:max_bytes].decode("utf-8", errors="ignore")
        omitted = len(encoded) - len(truncated.encode("utf-8"))
        return f"{truncated}\n...diff truncated ({omitted} bytes omitted)"

    def _run_git(self, args: list[str]) -> str:
        command = ["git", "-C", str(self.repo_root), *args]
        try:
            completed = subprocess.run(
                command,
                check=False,
                capture_output=True,
                text=True,
            )
        except OSError as err:
            raise GitError(f"git is not available: {err}") from err
        if completed.returncode != 0:
            quoted = " ".join(shlex.quote(part) for part in command)
            detail = completed.stderr.strip() or completed.stdout.strip() or "no error output"
            raise GitError(f"{quoted} failed with exit {completed.returncode}: {detail}")
        return completed.stdout


def _validate_diff_path(path: str) -> None:
    if not path or path != path.strip():
        raise GitError(f"invalid diff path: {path!r}")
    if path.startswith(("-", ":")):
        raise GitError(f"invalid diff path: {path!r}")
    if "\\" in path:
        raise GitError(f"invalid diff path: {path!r}")
    parsed = PurePosixPath(path)
    if parsed.is_absolute() or ".." in parsed.parts:
        raise GitError(f"invalid diff path: {path!r}")
