"""Proposal PR-ready state guard and offline replay/rubric evaluator."""

from __future__ import annotations

from dataclasses import replace

from .models import (
    Cluster,
    Destination,
    EvalFailureDestination,
    EvalState,
    Proposal,
    ProposalEvalArtifact,
    ReplayReport,
)
from .rubric import RubricOverride, RubricResult, rubric_markdown, score_proposal
from .util import promotion_threshold


class ProposalEvalBlocked(RuntimeError):
    """Raised when a proposal is not allowed to proceed to Builderbot pickup."""


def frequency_gate_blocking_reason(
    cluster: Cluster,
    *,
    destination: Destination | None = None,
) -> str | None:
    """Enforce the taxonomy promotion matrix on reconciled cluster frequency.

    critical needs 1 distinct PR, high 2, medium 3, low 5 — and low-severity themes may only be
    promoted when mechanically enforceable (test_or_linter).
    """
    severity = cluster.severity or "low"
    threshold = promotion_threshold(cluster.severity)
    if cluster.frequency < threshold:
        return f"below_frequency_threshold:{cluster.frequency}<{threshold}:{severity}"
    routed = destination or cluster.suggested_destination
    if severity == "low" and routed != "test_or_linter":
        return "low_severity_not_mechanically_enforceable"
    return None


def evaluate_proposal(
    proposal: Proposal,
    replay_report: ReplayReport,
    *,
    override: RubricOverride | None = None,
    matched_replay_case_ids: tuple[str, ...] = (),
) -> Proposal:
    """Run the scoring rubric and attach an eval artifact to one proposal."""
    running = replace(proposal, eval_state="eval_running", eval_passed=False)
    result = score_proposal(running, replay_report, override=override)
    state: EvalState = "eval_passed" if result.passed else "eval_failed"
    return replace(
        running,
        eval_state=state,
        eval_passed=result.passed,
        eval_artifact=_eval_artifact(
            running,
            result,
            state,
            matched_replay_case_ids=matched_replay_case_ids,
        ),
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
    frequency_reason = frequency_gate_blocking_reason(
        proposal.cluster,
        destination=proposal.destination,
    )
    if frequency_reason is not None:
        raise ProposalEvalBlocked(f"promotion frequency gate: {frequency_reason}")
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
    *,
    matched_replay_case_ids: tuple[str, ...] = (),
) -> ProposalEvalArtifact:
    return ProposalEvalArtifact(
        state=state,
        cluster_slug=proposal.cluster.slug,
        rubric_markdown=rubric_markdown(result),
        matched_replay_case_ids=matched_replay_case_ids,
        blocking_reasons=result.blocking_reasons,
        failure_destination=_failure_destination(result),
        manual_override=_manual_override_summary(result),
        llm_rubric_scores=dict(proposal.llm_rubric_scores),
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
