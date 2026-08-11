"""Linear cluster issue planning for accepted feedback-loop proposals (BKW-79)."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Callable, Literal

from .models import Proposal

BUILDERBOT_APPROVAL_LABEL = "code-engine:approved"
MAX_BUILDERBOT_DRAFT_BODY_CHARS = 4_000
MAX_BUILDERBOT_FILE_CONTENT_CHARS = 2_000
MAX_BUILDERBOT_SECTION_CHARS = 800

ClusterIssueStatus = Literal[
    "harvested",
    "classified",
    "needs_triage",
    "proposal_drafted",
    "eval_passed",
    "pr_open",
    "adopted",
    "rejected",
]

LINEAR_STATE_BY_CLUSTER_STATUS: dict[ClusterIssueStatus, str] = {
    "harvested": "Todo",
    "classified": "Todo",
    "needs_triage": "Todo",
    "proposal_drafted": "In Progress",
    "eval_passed": "In Progress",
    "pr_open": "In Review",
    "adopted": "Done",
    "rejected": "Canceled",
}

ClusterIssueWriter = Callable[["ClusterIssuePlan"], str]


@dataclass(frozen=True)
class ClusterIssuePlan:
    """Idempotent Linear upsert payload for one feedback cluster."""

    idempotency_key: str
    title: str
    description: str
    team_key: str
    project_name: str
    status: ClusterIssueStatus
    linear_state: str
    assignee: str | None = None
    labels: tuple[str, ...] = ()


@dataclass(frozen=True)
class ClusterIssueResult:
    """Result of creating/updating one Linear cluster issue."""

    plan: ClusterIssuePlan
    issue_url: str = ""


def build_cluster_issue_plan(
    proposal: Proposal,
    *,
    pr_url: str = "",
    status: ClusterIssueStatus = "proposal_drafted",
    team_key: str = "BKW",
    project_name: str = "Bitkey Feedback Loop",
    trigger_builderbot: bool = False,
    validation_commands: tuple[str, ...] = (),
    draft_pr_title: str = "",
    draft_pr_body: str = "",
    window: dict | None = None,
    suggested_replay_case: dict | None = None,
) -> ClusterIssuePlan:
    """Build a Linear issue upsert plan without assigning an owner."""
    from .cluster_memory import build_cluster_memory_issue_plan

    return build_cluster_memory_issue_plan(
        proposal.cluster,
        proposal=proposal,
        pr_url=pr_url,
        status=status,
        team_key=team_key,
        project_name=project_name,
        trigger_builderbot=trigger_builderbot,
        validation_commands=validation_commands,
        draft_pr_title=draft_pr_title,
        draft_pr_body=draft_pr_body,
        window=window,
        suggested_replay_case=suggested_replay_case,
    )


def create_or_update_cluster_issue(
    plan: ClusterIssuePlan,
    writer: ClusterIssueWriter,
) -> ClusterIssueResult:
    """Call an injected idempotent Linear writer for one cluster issue plan."""
    return ClusterIssueResult(plan=plan, issue_url=writer(plan))


def linear_state_for_cluster_status(status: ClusterIssueStatus) -> str:
    """Return the Bitkey Linear workflow state for one feedback-loop status."""
    return LINEAR_STATE_BY_CLUSTER_STATUS[status]


def _builderbot_lines(trigger_builderbot: bool) -> list[str]:
    if trigger_builderbot:
        return [
            f"- Trigger label: `{BUILDERBOT_APPROVAL_LABEL}`",
            "- Expected automation: Builderbot code engine opens a draft PR and comments back in Linear.",
        ]
    return [
        f"- Trigger label: `{BUILDERBOT_APPROVAL_LABEL}` not applied in this plan.",
    ]


def _builderbot_context_lines(
    proposal: Proposal,
    draft_pr_title: str,
    draft_pr_body: str,
) -> list[str]:
    lines = [
        f"- Draft PR title: {draft_pr_title or 'n/a'}",
        "- Expected change shape:",
        f"  - Scope: {_section_excerpt(proposal, 'scope', 'No scope section was provided.')}",
        f"  - Examples: {_section_excerpt(proposal, 'examples', 'No examples section was provided.')}",
        f"  - Rollback: {_section_excerpt(proposal, 'rollback_guidance', 'No rollback guidance was provided.')}",
        "",
        "### Draft PR body excerpt",
        *_fenced_excerpt(draft_pr_body, "markdown", MAX_BUILDERBOT_DRAFT_BODY_CHARS),
        "",
        "### Proposed file change excerpts",
    ]
    if not proposal.file_changes:
        return [*lines, "- n/a"]
    for change in proposal.file_changes:
        lines.extend(
            [
                f"- Path: `{change.path}`",
                f"  - Mode: `{change.mode}`",
                f"  - Content: {_content_size(change.content)}",
                *_indented_fenced_excerpt(
                    change.content,
                    "text",
                    MAX_BUILDERBOT_FILE_CONTENT_CHARS,
                    indent="  ",
                ),
            ]
        )
    return lines


def _item_lines(title: str, items: list[str] | tuple[str, ...]) -> list[str]:
    lines = [f"- {title}:"]
    if not items:
        return [*lines, "  - n/a"]
    return [*lines, *(f"  - {item}" for item in items)]


def _content_size(content: str) -> str:
    line_count = len(content.splitlines())
    byte_count = len(content.encode("utf-8"))
    return f"{line_count} line(s), {byte_count} byte(s)"


def _section_excerpt(proposal: Proposal, key: str, default: str) -> str:
    content = " ".join(proposal.sections.get(key, default).split())
    if len(content) <= MAX_BUILDERBOT_SECTION_CHARS:
        return content
    omitted = len(content) - MAX_BUILDERBOT_SECTION_CHARS
    excerpt = content[:MAX_BUILDERBOT_SECTION_CHARS].rstrip()
    return f"{excerpt} ... truncated {omitted} character(s)"


def _fenced_excerpt(content: str, language: str, max_chars: int) -> list[str]:
    if not content:
        return ["n/a"]
    excerpt = content[:max_chars]
    suffix = "" if len(content) <= max_chars else (
        f"\n... truncated {len(content) - max_chars} character(s)"
    )
    fence = _markdown_fence(excerpt)
    return [f"{fence}{language}", f"{excerpt}{suffix}", fence]


def _markdown_fence(content: str) -> str:
    fence = "```"
    while fence in content:
        fence += "`"
    return fence


def _indented_fenced_excerpt(
    content: str,
    language: str,
    max_chars: int,
    *,
    indent: str,
) -> list[str]:
    return [f"{indent}{line}" for line in _fenced_excerpt(content, language, max_chars)]


def _eval_markdown(proposal: Proposal) -> str:
    if proposal.eval_artifact is None:
        return "No eval artifact was attached."
    return proposal.eval_artifact.rubric_markdown
