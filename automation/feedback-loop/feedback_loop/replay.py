"""Replay corpus loader and current-vs-proposed harness."""

from __future__ import annotations

from collections import Counter
import json
import re
from pathlib import Path
from typing import Any, Callable, Iterable, get_args

from .models import (
    Destination,
    PrimaryClass,
    ReplayCase,
    ReplayCaseAssessment,
    ReplayCaseResult,
    ReplayCommitRange,
    ReplayFinding,
    ReplayReport,
    ReplayRunSummary,
    ReplayRuntimeFailure,
    Severity,
    SignalKind,
)

DEFAULT_CORPUS_PATH = Path(__file__).resolve().parents[1] / "replay" / "corpus.json"
CORPUS_VERSION = 1
REPLAY_REPORT_VERSION = 1

ReplayRunner = Callable[[ReplayCase], Iterable[ReplayFinding] | None]

_REQUIRED_CASE_FIELDS = frozenset(
    {
        "id",
        "repo",
        "pr_number",
        "pr_url",
        "commit_range",
        "changed_files",
        "miss_class",
        "source_comment_url",
        "expected_finding",
        "summary",
    }
)
_EXPECTED_FINDING_TOKEN_RE = re.compile(r"[a-z0-9]+")
_EXPECTED_FINDING_STOP_WORDS = frozenset(
    {
        "a",
        "an",
        "and",
        "or",
        "the",
        "to",
        "for",
        "that",
        "this",
        "with",
        "whose",
    }
)


def load_replay_corpus(path: str | Path | None = None) -> list[ReplayCase]:
    """Load and validate the committed replay corpus."""
    corpus_path = Path(path) if path is not None else DEFAULT_CORPUS_PATH
    data = json.loads(corpus_path.read_text(encoding="utf-8"))
    if data.get("version") != CORPUS_VERSION:
        raise ValueError(f"unsupported replay corpus version: {data.get('version')!r}")
    cases = data.get("cases")
    if not isinstance(cases, list):
        raise ValueError("replay corpus must contain a cases list")
    replay_cases = [_replay_case_from_json(item, index=index) for index, item in enumerate(cases)]
    case_id_counts = Counter(case.case_id for case in replay_cases)
    duplicates = sorted(case_id for case_id, count in case_id_counts.items() if count > 1)
    if duplicates:
        raise ValueError(f"replay corpus duplicate case id(s): {', '.join(duplicates)}")
    return replay_cases


def run_replay_harness(
    current_runner: ReplayRunner,
    proposed_runner: ReplayRunner,
    *,
    cases: Iterable[ReplayCase] | None = None,
    corpus_path: str | Path | None = None,
    current_name: str = "current",
    proposed_name: str = "proposed",
) -> ReplayReport:
    """Run current and proposed guidance over the same replay cases.

    The harness is pure: callers provide the runners and choose whether to write the returned
    artifact anywhere. A later publication stage wires evaluated proposals into emitted artifacts.
    """
    if cases is not None and corpus_path is not None:
        raise ValueError("pass either replay cases or a corpus path, not both")

    current_name = _guidance_name(current_name)
    proposed_name = _guidance_name(proposed_name)
    replay_cases = tuple(cases if cases is not None else load_replay_corpus(corpus_path))
    if not replay_cases:
        raise ValueError("replay harness requires at least one case")

    case_results: list[ReplayCaseResult] = []
    for case in replay_cases:
        current = _assess_case(case, current_name, current_runner)
        proposed = _assess_case(case, proposed_name, proposed_runner)
        case_results.append(ReplayCaseResult(case=case, current=current, proposed=proposed))

    return ReplayReport(
        case_results=tuple(case_results),
        current_summary=_summarize(current_name, [result.current for result in case_results]),
        proposed_summary=_summarize(proposed_name, [result.proposed for result in case_results]),
    )


def replay_report_artifact(report: ReplayReport) -> dict[str, Any]:
    """Return a deterministic JSON-compatible replay artifact."""
    return {
        "version": REPLAY_REPORT_VERSION,
        "proposal_publishable": report.proposal_publishable,
        "summaries": {
            "current": _summary_artifact(report.current_summary),
            "proposed": _summary_artifact(report.proposed_summary),
        },
        "cases": [_case_result_artifact(result) for result in report.case_results],
    }


def write_replay_report(report: ReplayReport, path: str | Path) -> None:
    """Write a deterministic replay artifact to an explicit caller-owned path."""
    artifact_path = Path(path)
    artifact_path.write_text(
        json.dumps(replay_report_artifact(report), indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )


def _replay_case_from_json(item: Any, *, index: int) -> ReplayCase:
    if not isinstance(item, dict):
        raise ValueError(f"replay case at index {index} must be an object")
    missing = sorted(_REQUIRED_CASE_FIELDS - item.keys())
    if missing:
        raise ValueError(f"replay case at index {index} missing fields: {', '.join(missing)}")

    commit_range = _commit_range_from_json(item["commit_range"], index=index)
    changed_files = _non_empty_string_tuple(item["changed_files"], "changed_files", index=index)
    labels = _string_tuple(item.get("labels", ()), "labels", index=index)

    miss_class = _validated_literal(item["miss_class"], PrimaryClass, "miss_class", index=index)
    source_kind = _validated_literal(
        item.get("source_kind", "review_comment"),
        SignalKind,
        "source_kind",
        index=index,
    )
    expected_destination = item.get("expected_destination")
    if expected_destination is not None:
        expected_destination = _validated_literal(
            expected_destination,
            Destination,
            "expected_destination",
            index=index,
        )
    expected_severity = item.get("expected_severity")
    if expected_severity is not None:
        expected_severity = _validated_literal(
            expected_severity,
            Severity,
            "expected_severity",
            index=index,
        )

    return ReplayCase(
        case_id=_required_string(item["id"], "id", index=index),
        repo=_required_string(item["repo"], "repo", index=index),
        pr_number=_positive_int(item["pr_number"], "pr_number", index=index),
        pr_url=_required_string(item["pr_url"], "pr_url", index=index),
        commit_range=commit_range,
        changed_files=changed_files,
        miss_class=miss_class,
        source_kind=source_kind,
        source_comment_url=_required_string(item["source_comment_url"], "source_comment_url", index=index),
        expected_destination=expected_destination,
        expected_severity=expected_severity,
        expected_finding=_required_string(item["expected_finding"], "expected_finding", index=index),
        summary=_required_string(item["summary"], "summary", index=index),
        labels=labels,
    )


def _assess_case(
    case: ReplayCase,
    guidance: str,
    runner: ReplayRunner,
) -> ReplayCaseAssessment:
    try:
        findings = _normalized_findings(runner(case))
    except Exception as err:
        failure = ReplayRuntimeFailure(
            guidance=guidance,
            case_id=case.case_id,
            exception_type=err.__class__.__name__,
            message=str(err),
        )
        return ReplayCaseAssessment(
            guidance=guidance,
            caught_miss=False,
            runtime_failure=failure,
        )

    expected_findings: list[ReplayFinding] = []
    extra_findings: list[ReplayFinding] = []
    for finding in findings:
        if _matches_expected_finding(case, finding):
            expected_findings.append(finding)
        else:
            extra_findings.append(finding)

    return ReplayCaseAssessment(
        guidance=guidance,
        caught_miss=bool(expected_findings),
        findings=findings,
        extra_findings=tuple(extra_findings),
    )


def _guidance_name(name: str) -> str:
    name = name.strip()
    if not name:
        raise ValueError("replay guidance names must be non-empty")
    return name


def _normalized_findings(findings: Iterable[ReplayFinding] | None) -> tuple[ReplayFinding, ...]:
    if findings is None:
        return ()
    normalized: list[ReplayFinding] = []
    for finding in findings:
        if not isinstance(finding, ReplayFinding):
            raise TypeError("replay runners must return ReplayFinding instances")
        if not finding.case_id.strip():
            raise ValueError("replay findings must include a case_id")
        if not finding.summary.strip():
            raise ValueError("replay findings must include a summary")
        normalized.append(finding)
    return tuple(
        sorted(
            normalized,
            key=lambda item: (
                item.case_id,
                item.destination or "",
                item.source_url,
                item.summary,
            ),
        )
    )


def _matches_expected_finding(case: ReplayCase, finding: ReplayFinding) -> bool:
    if finding.case_id != case.case_id:
        return False
    if case.expected_destination is not None and finding.destination != case.expected_destination:
        return False
    if finding.source_url != case.source_comment_url:
        return False
    expected = _normalized_finding_tokens(case.expected_finding)
    actual = _normalized_finding_tokens(finding.summary)
    if not expected or not actual:
        return False
    matches = expected & actual
    required_matches = 1 if len(expected) == 1 else 2
    return len(matches) >= required_matches


def _normalized_finding_tokens(value: str) -> frozenset[str]:
    tokens: set[str] = set()
    for token in _EXPECTED_FINDING_TOKEN_RE.findall(value.casefold()):
        if token in _EXPECTED_FINDING_STOP_WORDS:
            continue
        tokens.add(token)
        tokens.add(_finding_token_stem(token))
    return frozenset(tokens)


def _finding_token_stem(token: str) -> str:
    for suffix in ("ing", "ed", "s"):
        if len(token) > len(suffix) + 3 and token.endswith(suffix):
            stem = token[: -len(suffix)]
            if suffix == "ing" and stem.endswith("v"):
                return f"{stem}e"
            return stem
    return token


def _summarize(
    guidance: str,
    assessments: list[ReplayCaseAssessment],
) -> ReplayRunSummary:
    return ReplayRunSummary(
        guidance=guidance,
        caught_misses=sum(1 for assessment in assessments if assessment.caught_miss),
        missed_misses=sum(1 for assessment in assessments if assessment.missed_miss),
        extra_findings=sum(len(assessment.extra_findings) for assessment in assessments),
        runtime_failures=sum(1 for assessment in assessments if assessment.runtime_failure),
        blocking_failures=sum(1 for assessment in assessments if assessment.blocking_failure),
    )


def _summary_artifact(summary: ReplayRunSummary) -> dict[str, Any]:
    return {
        "guidance": summary.guidance,
        "caught_misses": summary.caught_misses,
        "missed_misses": summary.missed_misses,
        "extra_findings": summary.extra_findings,
        "runtime_failures": summary.runtime_failures,
        "blocking_failures": summary.blocking_failures,
    }


def _case_result_artifact(result: ReplayCaseResult) -> dict[str, Any]:
    return {
        "case_id": result.case.case_id,
        "pr_url": result.case.pr_url,
        "expected_finding": result.case.expected_finding,
        "current": _assessment_artifact(result.current),
        "proposed": _assessment_artifact(result.proposed),
    }


def _assessment_artifact(assessment: ReplayCaseAssessment) -> dict[str, Any]:
    return {
        "guidance": assessment.guidance,
        "caught_miss": assessment.caught_miss,
        "missed_miss": assessment.missed_miss,
        "blocking_failure": assessment.blocking_failure,
        "findings": [_finding_artifact(finding) for finding in assessment.findings],
        "extra_findings": [_finding_artifact(finding) for finding in assessment.extra_findings],
        "runtime_failure": (
            _runtime_failure_artifact(assessment.runtime_failure)
            if assessment.runtime_failure
            else None
        ),
    }


def _finding_artifact(finding: ReplayFinding) -> dict[str, Any]:
    return {
        "case_id": finding.case_id,
        "summary": finding.summary,
        "destination": finding.destination,
        "source_url": finding.source_url,
    }


def _runtime_failure_artifact(failure: ReplayRuntimeFailure) -> dict[str, Any]:
    return {
        "guidance": failure.guidance,
        "case_id": failure.case_id,
        "exception_type": failure.exception_type,
        "message": failure.message,
    }


def _commit_range_from_json(item: Any, *, index: int) -> ReplayCommitRange:
    if not isinstance(item, dict):
        raise ValueError(f"replay case at index {index} commit_range must be an object")
    return ReplayCommitRange(
        base=_required_string(item.get("base"), "commit_range.base", index=index),
        head=_required_string(item.get("head"), "commit_range.head", index=index),
        merge_commit=_string_or_empty(item.get("merge_commit"), "commit_range.merge_commit", index=index),
    )


def _validated_literal(value: Any, literal_type: object, field: str, *, index: int) -> Any:
    valid_values = set(get_args(literal_type))
    if value not in valid_values:
        raise ValueError(f"replay case at index {index} has invalid {field}: {value!r}")
    return value


def _required_string(value: Any, field: str, *, index: int) -> str:
    if not isinstance(value, str) or not value.strip():
        raise ValueError(f"replay case at index {index} field {field} must be a non-empty string")
    return value


def _string_or_empty(value: Any, field: str, *, index: int) -> str:
    if value is None:
        return ""
    if not isinstance(value, str):
        raise ValueError(f"replay case at index {index} field {field} must be a string")
    return value


def _positive_int(value: Any, field: str, *, index: int) -> int:
    if not isinstance(value, int) or value <= 0:
        raise ValueError(f"replay case at index {index} field {field} must be a positive integer")
    return value


def _non_empty_string_tuple(value: Any, field: str, *, index: int) -> tuple[str, ...]:
    items = _string_tuple(value, field, index=index)
    if not items:
        raise ValueError(f"replay case at index {index} field {field} must not be empty")
    return items


def _string_tuple(value: Any, field: str, *, index: int) -> tuple[str, ...]:
    if not isinstance(value, (list, tuple)):
        raise ValueError(f"replay case at index {index} field {field} must be a list")
    if not all(isinstance(item, str) and item.strip() for item in value):
        raise ValueError(f"replay case at index {index} field {field} must contain strings")
    return tuple(value)
