"""Replay scoring rubric for guardrail proposals."""

from __future__ import annotations

from dataclasses import dataclass
from typing import get_args

from .models import Proposal, ReplayCaseResult, ReplayReport, Severity

VALID_SEVERITIES = frozenset(get_args(Severity))
MANUAL_OVERRIDE_SEVERITIES = frozenset({"critical", "high"})
SPARSE_REPLAY_EVIDENCE = "sparse_replay_evidence"


@dataclass(frozen=True)
class RubricThresholds:
    """Minimum quality bar before a proposal can be published."""

    minimum_recall: float = 1.0
    maximum_noise_cost: float = 0.5
    minimum_actionability: float = 1.0
    minimum_source_grounding: float = 1.0
    minimum_replay_cases: int = 2
    require_no_runtime_failures: bool = True


@dataclass(frozen=True)
class RubricOverride:
    """Human override for high-severity proposals with sparse replay evidence."""

    approver: str
    rationale: str


@dataclass(frozen=True)
class RubricScores:
    """Objective scoring dimensions for one proposal replay."""

    recall: float
    noise_cost: float
    severity: float
    actionability: float
    source_grounding: float
    runtime_failures: int
    replay_cases: int
    overall: float


@dataclass(frozen=True)
class RubricResult:
    """Final scoring decision for a proposal."""

    passed: bool
    passed_without_override: bool
    manual_override_allowed: bool
    manual_override_applied: bool
    scores: RubricScores
    blocking_reasons: tuple[str, ...] = ()
    notes: tuple[str, ...] = ()
    override: RubricOverride | None = None


def score_proposal(
    proposal: Proposal,
    replay_report: ReplayReport,
    *,
    thresholds: RubricThresholds = RubricThresholds(),
    override: RubricOverride | None = None,
) -> RubricResult:
    """Score a guardrail proposal against replay results and proposal quality."""
    scores = _rubric_scores(proposal, replay_report)
    blocking_reasons = _blocking_reasons(scores, thresholds)
    passed_without_override = not blocking_reasons
    override_allowed = _manual_override_allowed(proposal, blocking_reasons)
    override_applied = bool(override and override_allowed and _valid_override(override))
    passed = passed_without_override or override_applied

    notes = _notes(scores)
    if override and not override_applied:
        notes = (*notes, "manual override ignored because it is not allowed for this failure")

    return RubricResult(
        passed=passed,
        passed_without_override=passed_without_override,
        manual_override_allowed=override_allowed,
        manual_override_applied=override_applied,
        scores=scores,
        blocking_reasons=tuple(blocking_reasons),
        notes=notes,
        override=override if override_applied else None,
    )


def rubric_markdown(result: RubricResult) -> str:
    """Render rubric results for draft PR descriptions."""
    status = "PASS" if result.passed else "FAIL"
    lines = [
        "## Replay rubric",
        f"Status: {status}",
        "",
        "| Metric | Value |",
        "|---|---:|",
        f"| Recall | {result.scores.recall:.2f} |",
        f"| Noise cost | {result.scores.noise_cost:.2f} |",
        f"| Severity | {result.scores.severity:.2f} |",
        f"| Actionability | {result.scores.actionability:.2f} |",
        f"| Source grounding | {result.scores.source_grounding:.2f} |",
        f"| Runtime failures | {result.scores.runtime_failures} |",
        f"| Replay cases | {result.scores.replay_cases} |",
        f"| Overall | {result.scores.overall:.2f} |",
    ]
    if result.blocking_reasons:
        lines.extend(["", "Blocking reasons:", *_bullet_lines(result.blocking_reasons)])
    if result.manual_override_allowed:
        lines.append("")
        lines.append("Manual override allowed for high-severity sparse replay evidence.")
    if result.manual_override_applied and result.override:
        lines.append(f"Manual override: {result.override.approver} - {result.override.rationale}")
    if result.notes:
        lines.extend(["", "Notes:", *_bullet_lines(result.notes)])
    return "\n".join(lines)


def _rubric_scores(proposal: Proposal, replay_report: ReplayReport) -> RubricScores:
    case_count = len(replay_report.case_results)
    recall = _ratio(replay_report.proposed_summary.caught_misses, case_count)
    noise_cost = _ratio(replay_report.proposed_summary.extra_findings, case_count)
    severity = _severity_score(proposal, replay_report.case_results)
    actionability = _actionability_score(proposal)
    source_grounding = _source_grounding_score(proposal, replay_report.case_results)
    runtime_failures = replay_report.proposed_summary.runtime_failures
    overall = _overall_score(
        recall=recall,
        noise_cost=noise_cost,
        severity=severity,
        actionability=actionability,
        source_grounding=source_grounding,
        runtime_failures=runtime_failures,
    )
    return RubricScores(
        recall=recall,
        noise_cost=noise_cost,
        severity=severity,
        actionability=actionability,
        source_grounding=source_grounding,
        runtime_failures=runtime_failures,
        replay_cases=case_count,
        overall=overall,
    )


def _blocking_reasons(
    scores: RubricScores,
    thresholds: RubricThresholds,
) -> list[str]:
    reasons: list[str] = []
    if scores.replay_cases < thresholds.minimum_replay_cases:
        reasons.append(SPARSE_REPLAY_EVIDENCE)
    if scores.recall < thresholds.minimum_recall:
        reasons.append("recall_below_threshold")
    if scores.noise_cost > thresholds.maximum_noise_cost:
        reasons.append("noise_cost_above_threshold")
    if scores.severity < 1.0:
        reasons.append("severity_mismatch")
    if scores.actionability < thresholds.minimum_actionability:
        reasons.append("proposal_not_actionable")
    if scores.source_grounding < thresholds.minimum_source_grounding:
        reasons.append("proposal_not_source_grounded")
    if thresholds.require_no_runtime_failures and scores.runtime_failures:
        reasons.append("runtime_failures_present")
    return reasons


def _manual_override_allowed(proposal: Proposal, blocking_reasons: list[str]) -> bool:
    if proposal.cluster.severity not in MANUAL_OVERRIDE_SEVERITIES:
        return False
    return set(blocking_reasons) == {SPARSE_REPLAY_EVIDENCE}


def _severity_score(
    proposal: Proposal,
    case_results: tuple[ReplayCaseResult, ...],
) -> float:
    expected_severities = [
        result.case.expected_severity
        for result in case_results
        if result.case.expected_severity is not None
    ]
    if expected_severities:
        matches = all(proposal.cluster.severity == item for item in expected_severities)
        return 1.0 if matches else 0.0
    return 1.0 if proposal.cluster.severity in VALID_SEVERITIES else 0.0


def _valid_override(override: RubricOverride) -> bool:
    return bool(override.approver.strip() and override.rationale.strip())


def _notes(scores: RubricScores) -> tuple[str, ...]:
    notes: list[str] = []
    if scores.noise_cost:
        notes.append("extra findings recorded as replay noise/regression cost")
    if scores.runtime_failures:
        notes.append("runtime failures block proposal publication")
    return tuple(notes)


def _actionability_score(proposal: Proposal) -> float:
    checks = [
        bool(proposal.target_artifacts),
        bool(proposal.validation_commands),
        bool(proposal.replay_cases),
        _specific_section(proposal.sections.get("scope", "")),
        _specific_section(proposal.sections.get("validation_steps", "")),
    ]
    return _ratio(sum(1 for check in checks if check), len(checks))


def _source_grounding_score(
    proposal: Proposal,
    case_results: tuple[ReplayCaseResult, ...],
) -> float:
    checks = [
        bool(proposal.evidence_urls),
        _specific_section(proposal.sections.get("evidence", "")),
        _caught_findings_are_grounded(case_results),
    ]
    return _ratio(sum(1 for check in checks if check), len(checks))


def _caught_findings_are_grounded(case_results: tuple[ReplayCaseResult, ...]) -> bool:
    caught_findings = []
    for result in case_results:
        caught_findings.extend(
            finding
            for finding in result.proposed.findings
            if finding.case_id == result.case.case_id
        )
    return bool(caught_findings) and all(finding.source_url for finding in caught_findings)


def _specific_section(text: str) -> bool:
    return len(text.split()) >= 8


def _overall_score(
    *,
    recall: float,
    noise_cost: float,
    severity: float,
    actionability: float,
    source_grounding: float,
    runtime_failures: int,
) -> float:
    runtime_penalty = 1.0 if runtime_failures else 0.0
    score = (
        recall * 0.4
        + severity * 0.15
        + actionability * 0.2
        + source_grounding * 0.2
        + max(0.0, 1.0 - noise_cost) * 0.05
        - runtime_penalty
    )
    return round(max(0.0, min(score, 1.0)), 2)


def _ratio(numerator: int | float, denominator: int | float) -> float:
    if denominator <= 0:
        return 0.0
    return round(numerator / denominator, 2)


def _bullet_lines(items: tuple[str, ...]) -> list[str]:
    return [f"- {item}" for item in items]
