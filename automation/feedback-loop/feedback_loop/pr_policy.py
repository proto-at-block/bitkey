"""One-change-per-feedback PR policy for generated guidance PRs (BKW-73)."""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import PurePosixPath

from .models import Destination, Proposal

AI_AGENTS_MD_SOURCES = frozenset(
    {
        ".ai/AGENTS.md",
        "app/.ai/AGENTS.md",
        "server/.ai/AGENTS.md",
        "firmware/.ai/AGENTS.md",
    }
)

TEST_OR_LINTER_DIR_NAMES = frozenset(
    {
        "__tests__",
        "fixture",
        "fixtures",
        "golden",
        "goldens",
        "snapshot",
        "snapshots",
        "test",
        "testdata",
        "testing",
        "tests",
    }
)

TEST_OR_LINTER_FILE_NAMES = frozenset(
    {
        ".eslintrc",
        ".eslintrc.cjs",
        ".eslintrc.js",
        ".eslintrc.json",
        ".swiftlint.yml",
        "clippy.toml",
        "detekt.yml",
        "detekt.yaml",
        "eslint.config.js",
        "eslint.config.mjs",
        "ktlint.yml",
        "lint.xml",
        "mypy.ini",
        "pytest.ini",
        "ruff.toml",
        "rustfmt.toml",
    }
)

TEST_OR_LINTER_FILE_SUFFIXES = (
    ".spec.js",
    ".spec.jsx",
    ".spec.ts",
    ".spec.tsx",
    ".test.js",
    ".test.jsx",
    ".test.ts",
    ".test.tsx",
    "_spec.rb",
    "_test.go",
    "_test.py",
    "_test.rs",
    "_tests.py",
)

TEST_OR_LINTER_WORKFLOW_TOKENS = frozenset(
    {"check", "checks", "ci", "lint", "test", "tests", "validation"}
)


class PrPolicyBlocked(RuntimeError):
    """Raised when a generated PR would be too broad or unrelated."""


@dataclass(frozen=True)
class PrPolicyOverride:
    """Explicit human override for tightly coupled generated changes."""

    approver: str
    rationale: str


@dataclass(frozen=True)
class PrPolicyResult:
    """Result of checking one proposal against the PR policy."""

    passed: bool
    passed_without_override: bool
    override_applied: bool
    blocking_reasons: tuple[str, ...] = ()
    override: PrPolicyOverride | None = None


def validate_pr_policy(
    proposal: Proposal,
    *,
    override: PrPolicyOverride | None = None,
) -> PrPolicyResult:
    """Validate that one proposal maps to one coherent PR."""
    paths = _proposal_paths(proposal)
    blocking_reasons = _blocking_reasons(proposal.destination, paths)
    passed_without_override = not blocking_reasons
    override_applied = bool(
        blocking_reasons
        and not _has_hard_blocking_reason(blocking_reasons)
        and _valid_override(override)
    )
    return PrPolicyResult(
        passed=passed_without_override or override_applied,
        passed_without_override=passed_without_override,
        override_applied=override_applied,
        blocking_reasons=tuple(blocking_reasons),
        override=override if override_applied else None,
    )


def require_pr_policy_passed(result: PrPolicyResult) -> None:
    """Raise before handing a broad generated PR plan to Builderbot."""
    if result.passed:
        return
    reasons = ", ".join(result.blocking_reasons)
    raise PrPolicyBlocked(f"generated PR failed one-change policy: {reasons}")


def _has_hard_blocking_reason(blocking_reasons: list[str]) -> bool:
    return any(
        reason.startswith("invalid_path")
        or reason.startswith("duplicate_normalized_path")
        or reason == "missing_file_changes"
        for reason in blocking_reasons
    )


def reviewer_checklist_markdown(result: PrPolicyResult) -> str:
    """Render the reviewer checklist required on every generated PR."""
    lines = [
        "## Reviewer checklist",
        "- [ ] Evidence quality: linked source PRs/comments support the proposed guardrail.",
        "- [ ] Destination choice: the change uses the most enforceable correct destination.",
        "- [ ] Eval results: replay/rubric results are present and acceptable.",
        "- [ ] Noise risk: false-positive or broad-match risk is understood.",
        "- [ ] Source-of-truth: AI guidance changes edit source files, not generated artifacts.",
        "- [ ] Rollback: the PR can be reverted or narrowed cleanly if it causes noise.",
    ]
    if result.override_applied and result.override:
        lines.extend(
            [
                "",
                "Policy override:",
                f"- {result.override.approver}: {result.override.rationale}",
            ]
        )
    return "\n".join(lines)


def _blocking_reasons(destination: Destination, paths: tuple[str, ...]) -> list[str]:
    reasons: list[str] = []
    paths, invalid_paths, duplicate_paths = _normalize_policy_paths(paths)
    if invalid_paths:
        reasons.append(f"invalid_path: {', '.join(invalid_paths)}")
    if duplicate_paths:
        reasons.append(f"duplicate_normalized_path: {', '.join(duplicate_paths)}")
    if not paths and destination != "world_model":
        reasons.append("missing_file_changes")
    families = {family for family in (_path_family(path) for path in paths) if family}
    if len(families) > 1:
        reasons.append("multiple_artifact_families")
    mismatched = [path for path in paths if not _path_allowed(destination, path)]
    if mismatched:
        reasons.append(f"destination_path_mismatch: {', '.join(mismatched)}")
    return reasons


def _proposal_paths(proposal: Proposal) -> tuple[str, ...]:
    paths = (change.path for change in proposal.file_changes)
    return tuple(paths)


def _normalize_policy_paths(
    paths: tuple[str, ...],
) -> tuple[tuple[str, ...], tuple[str, ...], tuple[str, ...]]:
    normalized_paths: list[str] = []
    invalid_paths: list[str] = []
    duplicate_paths: list[str] = []
    seen_paths: set[str] = set()
    for path in paths:
        normalized = _normalize_policy_path(path)
        if normalized is None:
            invalid_paths.append(path)
        elif normalized in seen_paths:
            duplicate_paths.append(normalized)
        else:
            seen_paths.add(normalized)
            normalized_paths.append(normalized)
    return (
        tuple(normalized_paths),
        tuple(dict.fromkeys(invalid_paths)),
        tuple(dict.fromkeys(duplicate_paths)),
    )


def _normalize_policy_path(path: str) -> str | None:
    if (
        path != path.strip()
        or "\\" in path
        or any(ord(char) < 32 or ord(char) == 127 for char in path)
    ):
        return None
    candidate = PurePosixPath(path)
    if candidate.is_absolute() or not candidate.parts or ".." in candidate.parts:
        return None
    normalized = candidate.as_posix()
    if normalized == ".":
        return None
    return normalized


def _path_allowed(destination: Destination, path: str) -> bool:
    if destination == "agents_check":
        return path.startswith(".agents/checks/")
    if destination == "ai_skill":
        return path.startswith(".ai/skills/")
    if destination == "ai_agents_md":
        return path in AI_AGENTS_MD_SOURCES
    if destination == "docs":
        return _docs_path(path)
    if destination == "test_or_linter":
        return _test_or_linter_path(path)
    if destination == "world_model":
        return not path
    return False


def _path_family(path: str) -> str:
    if path.startswith(".agents/checks/"):
        return "agents_check"
    if path.startswith(".ai/") or "/.ai/" in path:
        return "ai"
    if path.startswith("docs/") or path.endswith("README.md"):
        return "docs"
    return "code"


def _docs_path(path: str) -> bool:
    if _ai_guidance_path(path):
        return False
    return path.startswith("docs/") or path.endswith("README.md")


def _ai_guidance_path(path: str) -> bool:
    return (
        path.startswith(".agents/")
        or "/.agents/" in path
        or path.startswith(".ai/")
        or "/.ai/" in path
    )


def _test_or_linter_path(path: str) -> bool:
    original_parts = [part for part in path.split("/") if part]
    parts = [part.lower() for part in original_parts]
    if not parts:
        return False
    filename = parts[-1]
    original_filename = original_parts[-1]
    return (
        any(part in TEST_OR_LINTER_DIR_NAMES for part in parts[:-1])
        or _kmp_test_source_set_path(parts)
        or filename in TEST_OR_LINTER_FILE_NAMES
        or filename.startswith(("lint_", "test_"))
        or filename.endswith(TEST_OR_LINTER_FILE_SUFFIXES)
        or _platform_test_filename(original_filename)
        or _workflow_config_path(parts, filename)
    )


def _platform_test_filename(filename: str) -> bool:
    return filename.endswith(("Test.kt", "Tests.kt", "Test.swift", "Tests.swift"))


def _kmp_test_source_set_path(parts: list[str]) -> bool:
    for index, part in enumerate(parts[:-1]):
        if part != "src" or index + 1 >= len(parts) - 1:
            continue
        source_set = parts[index + 1]
        if source_set != "test" and source_set.endswith("test"):
            return True
    return False


def _workflow_config_path(parts: list[str], filename: str) -> bool:
    if len(parts) < 3 or parts[0] != ".github" or parts[1] != "workflows":
        return False
    if not filename.endswith((".yaml", ".yml")):
        return False
    workflow_name = filename.rsplit(".", maxsplit=1)[0]
    tokens = workflow_name.replace("_", "-").split("-")
    return any(token in TEST_OR_LINTER_WORKFLOW_TOKENS for token in tokens)


def _valid_override(override: PrPolicyOverride | None) -> bool:
    return bool(override and override.approver.strip() and override.rationale.strip())
