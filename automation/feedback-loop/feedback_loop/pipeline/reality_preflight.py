"""Reality checks for LLM route plans.

These checks verify a plan against the actual repository, never against its own text: target
paths must land in real directories (or explicitly allowed new-path families), validation
commands must reference real paths and execute a real runner for the destination, and new
fixture/data files must be referenced by something that already exists. Keyword-presence checks
are deliberately absent — a planner model satisfies text-matching gates literally without
producing anything runnable.
"""

from __future__ import annotations

from pathlib import PurePosixPath
import re
import shlex
from typing import Any, Iterable

from ..models import Destination, ProposalFileChange
from ..repo_reality import RepoReality
from ..util import area_for_path, excerpt

MAX_GUIDANCE_EXCERPT_CHARS = 1_200

AI_CONTEXT_COMMANDS = (
    "./tools/ai-context/ai-context-generate.sh",
    "./tools/ai-context/ai-context-check.sh",
)

NEW_AGENTS_CHECK_RE = re.compile(r"\.agents/checks/[a-z0-9][a-z0-9-]*\.md")
NEW_SKILL_MD_RE = re.compile(r"\.ai/skills/[a-z0-9][a-z0-9-]*/SKILL\.md")
SKILL_MD_RE = re.compile(r"\.ai/skills/[^/]+/SKILL\.md")

# Commands that can only inspect text; they prove nothing about whether a check or test works.
INSPECTION_COMMAND_HEADS = frozenset(
    {
        "awk",
        "cat",
        "diff",
        "echo",
        "egrep",
        "fgrep",
        "file",
        "find",
        "grep",
        "head",
        "jq",
        "ls",
        "rg",
        "sed",
        "stat",
        "tail",
        "test",
        "true",
        "wc",
    }
)

# Generic tool heads that are not repo files but are real, locally runnable entrypoints.
KNOWN_TOOL_HEADS = frozenset(
    {
        "bin/ai-gradle",
        "cargo",
        "git",
        "gradle",
        "inv",
        "invoke",
        "make",
        "meson",
        "python",
        "python3",
        "sq",
    }
)
FORBIDDEN_COMMAND_HEADS = frozenset({"./gradlew", "gradlew"})

# Area -> command patterns that actually execute tests/lints for that part of the repo.
AREA_TEST_RUNNERS: dict[str, tuple[re.Pattern[str], ...]] = {
    "app": (
        re.compile(r"^bin/ai-gradle\s+\S+"),
        re.compile(r"^gradle\s+\S+"),
    ),
    "server": (re.compile(r"^cargo\s+(test|nextest|clippy|fmt)\b"),),
    "core": (re.compile(r"^cargo\s+(test|nextest|clippy|fmt)\b"),),
    "automation": (re.compile(r"^python3?\s+-m\s+(unittest|pytest)\b"),),
    "firmware": (
        re.compile(r"^inv(oke)?\s+\S+"),
        re.compile(r"^meson\s+test\b"),
    ),
}

DATA_FILE_SUFFIXES = (".csv", ".json", ".toml", ".txt", ".xml", ".yaml", ".yml")
FIXTURE_DIR_NAMES = frozenset(
    {"fixture", "fixtures", "golden", "goldens", "snapshot", "snapshots", "testdata"}
)


def path_reality_reasons(
    file_changes: Iterable[ProposalFileChange],
    destination: Destination,
    repo: RepoReality,
) -> list[str]:
    """Verify file-change targets land in the real tree or an allowed new-path family."""
    reasons: list[str] = []
    changes = list(file_changes)
    for change in changes:
        if change.mode == "unified_diff":
            if not repo.file_exists(change.path):
                reasons.append("unified_diff_target_missing")
            continue
        if repo.file_exists(change.path) or repo.parent_dir_exists(change.path):
            continue
        if NEW_AGENTS_CHECK_RE.fullmatch(change.path) and repo.dir_exists(".agents/checks"):
            continue
        if NEW_SKILL_MD_RE.fullmatch(change.path) and repo.dir_exists(".ai/skills"):
            continue
        reasons.append("nonexistent_parent_directory")
    if (
        destination == "ai_skill"
        and changes
        and not any(SKILL_MD_RE.fullmatch(change.path) for change in changes)
    ):
        reasons.append("missing_skill_md")
    return reasons


def validation_command_reasons(
    destination: Destination,
    commands: Iterable[str],
    file_change_paths: Iterable[str],
    repo: RepoReality,
) -> list[str]:
    """Verify validation commands reference real paths and execute a real runner."""
    command_list = [command for command in commands if command.strip()]
    if not command_list:
        return []
    created_paths = set(file_change_paths)
    reasons: list[str] = []
    kinds: list[str] = []

    area = plan_area(created_paths)
    for command in command_list:
        kind, command_reasons = _assess_command(command, destination, area, created_paths, repo)
        kinds.append(kind)
        reasons.extend(command_reasons)

    if destination != "docs" and kinds and all(kind == "inspection" for kind in kinds):
        reasons.append("vacuous_validation_commands")
    if destination == "test_or_linter" and "runner" not in kinds:
        reasons.append("missing_area_test_runner")
    if destination == "ai_skill" and not set(AI_CONTEXT_COMMANDS).issubset(set(command_list)):
        reasons.append("missing_ai_context_commands")
    return reasons


def validation_command_assessment(
    destination: Destination,
    commands: Iterable[str],
    file_change_paths: Iterable[str],
    repo: RepoReality,
) -> list[dict[str, str]]:
    """Deterministic runner/inspection classification, shared with the judge prompt input."""
    created_paths = set(file_change_paths)
    area = plan_area(created_paths)
    assessment: list[dict[str, str]] = []
    for command in commands:
        if not command.strip():
            continue
        kind, _ = _assess_command(command, destination, area, created_paths, repo)
        assessment.append({"command": command, "kind": kind})
    return assessment


def infrastructure_reality_reasons(
    destination: Destination,
    file_changes: Iterable[ProposalFileChange],
    repo: RepoReality,
) -> list[str]:
    """Reject new fixture/data files that nothing in the repository reads."""
    if destination not in {"test_or_linter", "agents_check"}:
        return []
    reasons: list[str] = []
    changes = list(file_changes)
    data_files = [change for change in changes if _is_data_file(change.path)]
    for change in data_files:
        if repo.file_exists(change.path):
            continue
        parent = change.path.rsplit("/", 1)[0] + "/" if "/" in change.path else ""
        filename = change.path.rsplit("/", 1)[-1]
        if parent and repo.references_in_repo(parent) > 0:
            continue
        if repo.references_in_repo(filename) > 0:
            continue
        reasons.append("unreferenced_new_fixture")
    if destination == "test_or_linter" and changes and len(data_files) == len(changes):
        reasons.append("data_only_test_plan")
    return reasons


def guidance_context(area: str, repo: RepoReality) -> dict[str, Any]:
    """Existing guidance the planner/judge must dedupe against."""
    scoped_path = repo.scoped_agents_md_path(area)
    context: dict[str, Any] = {
        "scoped_agents_md": _guidance_file(scoped_path, repo),
        "existing_agents_checks": [
            {"name": check.name, "description": check.description, "path": check.path}
            for check in repo.existing_checks()
        ],
        "existing_ai_skills": list(repo.existing_skills()),
    }
    if scoped_path != ".ai/AGENTS.md":
        context["root_agents_md"] = _guidance_file(".ai/AGENTS.md", repo)
    return context


def plan_area(file_change_paths: Iterable[str], fallback: str = "") -> str:
    """Repo area of a plan's file changes (one-change policy keeps them in one family)."""
    for path in sorted(file_change_paths):
        return area_for_path(path)
    return fallback or "repo-wide"


def _assess_command(
    command: str,
    destination: Destination,
    area: str,
    created_paths: set[str],
    repo: RepoReality,
) -> tuple[str, list[str]]:
    """Classify one command as runner/inspection/unknown and collect path-reality reasons."""
    reasons: list[str] = []
    segments = [segment.strip() for segment in command.split("&&") if segment.strip()]
    kind = "unknown"
    saw_known_head = False

    for segment in segments:
        try:
            tokens = shlex.split(segment)
        except ValueError:
            return "unknown", ["unknown_validation_command"]
        tokens = _strip_env_assignments(tokens)
        if not tokens:
            continue
        head = tokens[0]
        if head == "cd":
            reasons.extend(_cd_target_reasons(tokens[1:], repo))
            saw_known_head = True
            continue
        if head in FORBIDDEN_COMMAND_HEADS:
            reasons.append("forbidden_gradle_wrapper_command")
            saw_known_head = True
            continue
        if _is_runner_segment(segment, destination, area):
            kind = "runner"
            saw_known_head = True
        elif head in INSPECTION_COMMAND_HEADS:
            if kind != "runner":
                kind = "inspection"
            saw_known_head = True
        elif head in KNOWN_TOOL_HEADS or repo.file_exists(head.removeprefix("./")):
            saw_known_head = True
        reasons.extend(_path_token_reasons(tokens[1:], created_paths, repo))

    if not saw_known_head:
        reasons.append("unknown_validation_command")
    return kind, reasons


def _cd_target_reasons(tokens: list[str], repo: RepoReality) -> list[str]:
    if len(tokens) != 1:
        return ["invalid_cd_validation_directory"]
    raw_target = tokens[0].strip("'\"`,;")
    if (
        not raw_target
        or raw_target.startswith("-")
        or raw_target.startswith("~")
        or "://" in raw_target
        or any(char in raw_target for char in "*?[]{}$")
    ):
        return ["invalid_cd_validation_directory"]
    target = raw_target.removeprefix("./").rstrip("/")
    path = PurePosixPath(target)
    if path.is_absolute() or ".." in path.parts:
        return ["invalid_cd_validation_directory"]
    if target in ("", ".") or repo.dir_exists(path.as_posix()):
        return []
    return ["invalid_cd_validation_directory"]


def _is_runner_segment(segment: str, destination: Destination, area: str) -> bool:
    if destination == "agents_check":
        return segment.casefold().startswith("sq agents review")
    if destination in {"ai_agents_md", "ai_skill"}:
        return segment.startswith("./tools/ai-context/ai-context-")
    if destination == "test_or_linter":
        return any(pattern.match(segment) for pattern in AREA_TEST_RUNNERS.get(area, ()))
    return False


def _path_token_reasons(
    tokens: list[str],
    created_paths: set[str],
    repo: RepoReality,
) -> list[str]:
    reasons: list[str] = []
    for token in tokens:
        candidate = token.strip("'\"`,;")
        if (
            not candidate
            or candidate.startswith("-")
            or "://" in candidate
            or "/" not in candidate
            or any(char in candidate for char in "*?[]{}$")
        ):
            continue
        candidate = candidate.removeprefix("./")
        if (
            candidate in created_paths
            or repo.file_exists(candidate)
            or repo.dir_exists(candidate)
        ):
            continue
        reasons.append("nonexistent_validation_path")
    return reasons


def _strip_env_assignments(tokens: list[str]) -> list[str]:
    index = 0
    while index < len(tokens) and re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*=.*", tokens[index]):
        index += 1
    return tokens[index:]


def _is_data_file(path: str) -> bool:
    lowered = path.casefold()
    if lowered.endswith(DATA_FILE_SUFFIXES):
        return True
    parts = lowered.split("/")
    return any(part in FIXTURE_DIR_NAMES for part in parts[:-1])


def _guidance_file(path: str, repo: RepoReality) -> dict[str, Any]:
    content = repo.read_text(path)
    return {
        "path": path,
        "exists": content is not None,
        "excerpt": excerpt(content, MAX_GUIDANCE_EXCERPT_CHARS) if content else "",
    }
