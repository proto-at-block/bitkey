"""Stage 6: emit draft PR plans and Linear issues for Builderbot pickup.

The future implementation is the only stage that writes anything external, and only when cfg.dry_run
is False. Even then:
  - Linear cluster issues are created with NO assignee, idempotent on re-run (BKW-79);
  - Builderbot code-engine execution is triggered by the Linear `code-engine:approved` label;
  - a proposal MUST be PR-ready (eval_passed=True, or a recorded override, plus eval_state="pr_ready")
    or emit refuses it (BKW-63);
  - emitted Linear issues include evidence links for human review (BKW-60).
"""

from __future__ import annotations

from dataclasses import dataclass, replace

from ..config import RunConfig
from ..eval_gate import require_pr_ready
from ..linear_control import (
    ClusterIssuePlan,
    ClusterIssueWriter,
    build_cluster_issue_plan,
    create_or_update_cluster_issue,
)
from ..models import Proposal
from ..pr_policy import (
    PrPolicyOverride,
    PrPolicyResult,
    require_pr_policy_passed,
    reviewer_checklist_markdown,
    validate_pr_policy,
)

AI_CONTEXT_COMMANDS = (
    "./tools/ai-context/ai-context-generate.sh",
    "./tools/ai-context/ai-context-check.sh",
)


@dataclass(frozen=True)
class DraftPrPlan:
    """Reviewable draft PR content handed to Builderbot through Linear."""

    title: str
    body: str
    target_artifacts: tuple[str, ...]
    evidence_urls: tuple[str, ...]
    validation_commands: tuple[str, ...]
    draft: bool = True


@dataclass(frozen=True)
class EmitResult:
    """Result of preparing or creating one proposal PR."""

    proposal: Proposal
    draft_pr: DraftPrPlan
    cluster_issue: ClusterIssuePlan
    linear_issue_url: str = ""


def emit(
    cfg: RunConfig,
    proposals: list[Proposal],
    *,
    linear_writer: ClusterIssueWriter | None = None,
    policy_override: PrPolicyOverride | None = None,
) -> list[EmitResult]:
    """Build or create one Linear code-engine issue per eval-ready proposal."""
    for proposal in proposals:
        require_pr_ready(proposal)

    if not cfg.dry_run and linear_writer is None:
        raise NotImplementedError("emit.emit requires a Linear cluster issue writer for --execute")

    results = [
        _build_emit_result(
            cfg,
            proposal,
            policy_override=policy_override,
        )
        for proposal in proposals
    ]
    if cfg.dry_run:
        return results

    assert linear_writer is not None
    return [
        replace(
            result,
            linear_issue_url=create_or_update_cluster_issue(
                result.cluster_issue, linear_writer
            ).issue_url,
        )
        for result in results
    ]


def _build_emit_result(
    cfg: RunConfig,
    proposal: Proposal,
    *,
    policy_override: PrPolicyOverride | None = None,
) -> EmitResult:
    draft_pr = build_draft_pr_plan(
        proposal,
        policy_override=policy_override,
    )
    cluster_issue = build_cluster_issue_plan(
        proposal,
        status="eval_passed",
        trigger_builderbot=not cfg.dry_run,
        validation_commands=draft_pr.validation_commands,
        draft_pr_title=draft_pr.title,
        draft_pr_body=draft_pr.body,
    )
    return EmitResult(
        proposal=proposal,
        draft_pr=draft_pr,
        cluster_issue=cluster_issue,
    )


def build_draft_pr_plan(
    proposal: Proposal,
    *,
    policy_override: PrPolicyOverride | None = None,
) -> DraftPrPlan:
    """Create a draft PR title/body from one eval-ready proposal."""
    require_pr_ready(proposal)
    policy_result = validate_pr_policy(proposal, override=policy_override)
    require_pr_policy_passed(policy_result)
    validation_commands = _validation_commands(proposal)
    return DraftPrPlan(
        title=_draft_pr_title(proposal),
        body=_draft_pr_body(proposal, validation_commands, policy_result),
        target_artifacts=tuple(proposal.target_artifacts),
        evidence_urls=tuple(proposal.evidence_urls),
        validation_commands=validation_commands,
        draft=True,
    )


def _draft_pr_title(proposal: Proposal) -> str:
    destination = proposal.destination.replace("_", " ")
    summary = proposal.summary.split(".")[0].strip() or proposal.template_title
    if len(summary) > 72:
        summary = f"{summary[:69].rstrip()}..."
    return f"Feedback loop: {destination} guardrail - {summary}"


def _draft_pr_body(
    proposal: Proposal,
    validation_commands: tuple[str, ...],
    policy_result: PrPolicyResult,
) -> str:
    file_changes = [change.path for change in proposal.file_changes]
    lines = [
        "## Summary",
        proposal.summary,
        "",
        "## Proposal",
        f"- Destination: `{proposal.destination}`",
        f"- Template: {proposal.template_title or 'n/a'}",
        f"- Confidence: {proposal.confidence:.2f}",
        *_artifact_lines("Target artifacts", proposal.target_artifacts),
        *_artifact_lines("Proposed file changes", file_changes),
        *_artifact_lines("Evidence", proposal.evidence_urls),
        "",
        "## Eval results",
        _eval_markdown(proposal),
        "",
        "## Scope",
        proposal.sections.get("scope", "No scope section was provided."),
        "",
        "## Validation steps",
        *_checkbox_lines(validation_commands),
        "",
        "## Reviewer instructions",
        proposal.sections.get("reviewer_instructions", "Review the proposal scope and eval results."),
        "",
        reviewer_checklist_markdown(policy_result),
    ]
    return "\n".join(lines).strip() + "\n"


def _artifact_lines(title: str, items: tuple[str, ...] | list[str]) -> list[str]:
    lines = [f"- {title}:"]
    if not items:
        return [*lines, "  - n/a"]
    return [*lines, *(f"  - {item}" for item in items)]


def _checkbox_lines(commands: tuple[str, ...]) -> list[str]:
    if not commands:
        return ["- [ ] No validation command provided."]
    return [f"- [ ] `{command}`" for command in commands]


def _validation_commands(proposal: Proposal) -> tuple[str, ...]:
    commands = list(proposal.validation_commands)
    if _touches_ai_context(proposal):
        for command in AI_CONTEXT_COMMANDS:
            if command not in commands:
                commands.append(command)
    return tuple(commands)


def _touches_ai_context(proposal: Proposal) -> bool:
    paths = [*proposal.target_artifacts, *(change.path for change in proposal.file_changes)]
    return any(path.startswith(".ai/") or "/.ai/" in path for path in paths)


def _eval_markdown(proposal: Proposal) -> str:
    if proposal.eval_artifact is None:
        return "No eval artifact was attached."
    return proposal.eval_artifact.rubric_markdown
