"""Proposal eval gate for replay/rubric results."""

from __future__ import annotations

from dataclasses import replace

from .models import (
    EvalFailureDestination,
    EvalState,
    Proposal,
    ProposalEvalArtifact,
    ReplayReport,
)
from .rubric import RubricOverride, RubricResult, rubric_markdown, score_proposal


class ProposalEvalBlocked(RuntimeError):
    """Raised when a proposal is not allowed to proceed to Builderbot pickup."""


def evaluate_proposal(
    proposal: Proposal,
    replay_report: ReplayReport,
    *,
    override: RubricOverride | None = None,
) -> Proposal:
    """Run the scoring rubric and attach an eval artifact to one proposal."""
    running = replace(proposal, eval_state="eval_running", eval_passed=False)
    result = score_proposal(running, replay_report, override=override)
    state: EvalState = "eval_passed" if result.passed else "eval_failed"
    return replace(
        running,
        eval_state=state,
        eval_passed=result.passed,
        eval_artifact=_eval_artifact(running, result, state),
    )


def mark_pr_ready(proposal: Proposal, *, future_pr_url: str = "") -> Proposal:
    """Mark an eval-passed proposal as ready for the future emit stage."""
    if proposal.eval_state != "eval_passed" or not proposal.eval_passed:
        raise ProposalEvalBlocked("proposal must pass eval before it can become PR-ready")
    artifact = proposal.eval_artifact
    if artifact is None or artifact.state != "eval_passed":
        raise ProposalEvalBlocked("proposal must include an eval-passed artifact before it can become PR-ready")
    artifact = replace(artifact, state="pr_ready", future_pr_url=future_pr_url)
    return replace(proposal, eval_state="pr_ready", eval_artifact=artifact)


def require_pr_ready(proposal: Proposal) -> None:
    """Fail before any Builderbot-triggering Linear write if the proposal has not passed eval."""
    artifact = proposal.eval_artifact
    if proposal.eval_state == "pr_ready" and proposal.eval_passed and artifact and artifact.state == "pr_ready":
        return
    if artifact and artifact.blocking_reasons:
        reasons = ", ".join(artifact.blocking_reasons)
        raise ProposalEvalBlocked(f"proposal eval blocked Builderbot pickup: {reasons}")
    raise ProposalEvalBlocked("proposal must be eval-passed and PR-ready before emit")


def _eval_artifact(
    proposal: Proposal,
    result: RubricResult,
    state: EvalState,
) -> ProposalEvalArtifact:
    return ProposalEvalArtifact(
        state=state,
        cluster_theme=proposal.cluster.theme,
        rubric_markdown=rubric_markdown(result),
        blocking_reasons=result.blocking_reasons,
        failure_destination=_failure_destination(result),
        manual_override=_manual_override_summary(result),
    )


def _failure_destination(result: RubricResult) -> EvalFailureDestination:
    if result.passed:
        return "none"
    if result.blocking_reasons == ("sparse_replay_evidence",):
        return "research"
    return "triage"


def _manual_override_summary(result: RubricResult) -> str:
    if not result.manual_override_applied or result.override is None:
        return ""
    return f"{result.override.approver}: {result.override.rationale}"
