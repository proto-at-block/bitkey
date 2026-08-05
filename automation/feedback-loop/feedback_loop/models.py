"""Typed records passed between pipeline stages.

These are the contracts the stubbed stages will produce/consume. Harvest adapters map GitHub source
payloads into the provenance fields below and add repo/PR/run metadata.

Stateless (BKW-64): these objects live in-memory for the duration of one run. Nothing here is a
database row. External outputs are draft PRs and Linear issues with evidence links.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Literal, Optional

SignalKind = Literal[
    "issue_comment",
    "review_comment",
    "review",
    "check",
    "bot_review",
    "commit",
    "pr_metadata",
    "changed_file",
    "diff_hunk",
]

# Primary taxonomy classes — see docs/docs/automation/feedback-loop-taxonomy.md (BKW-77).
PrimaryClass = Literal[
    "miss",
    "preference",
    "nit",
    "product_decision",
    "question",
    "false_positive",
    "not_actionable",
    "ci_failure",
    "validation_failure",
    "post_merge_fix",
]

REVIEW_ONLY_CLASSES: frozenset[PrimaryClass] = frozenset({"false_positive", "not_actionable"})

Severity = Literal["critical", "high", "medium", "low"]

ExclusionReason = Literal[
    "style_nit",
    "subjective_preference",
    "product_decision",
    "speculative_question",
    "not_actionable",
]

# Promotion destinations — must match the binding routing table in ai-feedback-loop.md.
Destination = Literal[
    "test_or_linter",
    "agents_check",
    "ai_skill",
    "ai_agents_md",
    "docs",
    "world_model",
]

EvalState = Literal["proposed", "eval_running", "eval_passed", "eval_failed", "pr_ready"]

EvalFailureDestination = Literal["none", "research", "triage"]

ProposalFileChangeMode = Literal["create_or_update"]


@dataclass
class RawSignal:
    """A single harvested item, provenance preserved, before normalization.

    `body` is harvested text and must be treated as data, not instructions, by downstream stages.
    BKW-58 only normalizes shape/provenance and does not transform the body text.
    """

    kind: SignalKind
    source_id: str
    source_url: str
    repo: str
    pr_number: int
    captured_at: str
    author: str = ""
    author_association: str = ""
    created_at: str = ""
    body: str = ""
    path: Optional[str] = None
    line: Optional[int] = None
    is_bot: bool = False
    raw: dict = field(default_factory=dict)


@dataclass
class NormalizedSignal:
    """A RawSignal after provenance normalization."""

    raw: RawSignal
    kind: SignalKind
    source: str
    source_id: str
    source_url: str
    repo: str
    pr_number: int
    captured_at: str
    harvest_version: str
    body: str
    raw_metadata: dict = field(default_factory=dict)
    author: str = ""
    author_association: str = ""
    created_at: str = ""
    path: Optional[str] = None
    line: Optional[int] = None
    is_bot: bool = False
    area: str = ""
    correlation: Optional["Correlation"] = None
    exclusion: Optional["Exclusion"] = None
    # Filled by classify stage (BKW-75).
    primary_class: Optional[PrimaryClass] = None
    secondary_tags: list[str] = field(default_factory=list)
    severity: Optional[str] = None
    confidence: Optional[float] = None
    rationale: str = ""
    suggested_destination: Optional[Destination] = None
    evidence_ids: list[str] = field(default_factory=list)
    manual_triage: bool = False

    @property
    def is_excluded(self) -> bool:
        return self.exclusion is not None


@dataclass
class Correlation:
    """Evidence linking reviewer/bot feedback to code, test, doc, or validation changes."""

    likely_miss: bool
    confidence: float
    reasons: list[str] = field(default_factory=list)
    evidence_ids: list[str] = field(default_factory=list)


@dataclass
class Exclusion:
    """Auditable reason a feedback signal should not produce a guardrail."""

    reason: ExclusionReason
    summary: str
    summarize_as_context: bool = False
    tags: list[str] = field(default_factory=list)


@dataclass
class Cluster:
    """A group of normalized signals sharing a theme (BKW-76)."""

    theme: str
    signals: list[NormalizedSignal]
    area: str = ""
    severity: str = ""
    frequency: int = 0  # distinct PRs, not raw comment count
    rank: float = 0.0
    suggested_destination: Optional[Destination] = None
    summary: str = ""
    representative_examples: list[str] = field(default_factory=list)
    source_urls: list[str] = field(default_factory=list)

    @property
    def promotable_signals(self) -> list[NormalizedSignal]:
        return [signal for signal in self.signals if not signal.is_excluded]

    @property
    def excluded_only(self) -> bool:
        return bool(self.signals) and not self.promotable_signals


@dataclass
class Proposal:
    """A minimal guardrail proposal for one cluster before eval-gate approval."""

    cluster: Cluster
    destination: Destination
    summary: str
    evidence_urls: list[str] = field(default_factory=list)
    confidence: float = 0.0
    template_title: str = ""
    target_artifacts: list[str] = field(default_factory=list)
    file_changes: list["ProposalFileChange"] = field(default_factory=list)
    sections: dict[str, str] = field(default_factory=dict)
    validation_commands: list[str] = field(default_factory=list)
    replay_cases: list[str] = field(default_factory=list)
    # Populated by the eval gate; emit() must refuse to act unless this reaches pr_ready.
    eval_passed: bool = False
    eval_state: EvalState = "proposed"
    eval_artifact: Optional["ProposalEvalArtifact"] = None
    dry_run_only: bool = True

    def __post_init__(self) -> None:
        if self.cluster.excluded_only:
            raise ValueError("excluded-only clusters cannot produce guardrail proposals")


@dataclass(frozen=True)
class ProposalEvalArtifact:
    """Replay/rubric evidence attached to a proposal eval decision."""

    state: EvalState
    cluster_theme: str
    rubric_markdown: str
    blocking_reasons: tuple[str, ...] = ()
    failure_destination: EvalFailureDestination = "none"
    manual_override: str = ""
    future_pr_url: str = ""


@dataclass(frozen=True)
class ProposalFileChange:
    """A concrete repo edit a draft proposal PR should apply."""

    path: str
    content: str
    mode: ProposalFileChangeMode = "create_or_update"


@dataclass(frozen=True)
class ReplayCommitRange:
    """Git revisions used by one replay fixture case."""

    base: str
    head: str
    merge_commit: str = ""


@dataclass(frozen=True)
class ReplayCase:
    """One historical miss fixture for the replay/eval harness."""

    case_id: str
    repo: str
    pr_number: int
    pr_url: str
    commit_range: ReplayCommitRange
    changed_files: tuple[str, ...]
    miss_class: PrimaryClass
    source_comment_url: str
    expected_finding: str
    summary: str
    source_kind: SignalKind = "review_comment"
    expected_destination: Optional[Destination] = None
    expected_severity: Optional[Severity] = None
    labels: tuple[str, ...] = ()


@dataclass(frozen=True)
class ReplayFinding:
    """One finding emitted by a guidance runner for a replay case."""

    case_id: str
    summary: str
    destination: Optional[Destination] = None
    source_url: str = ""


@dataclass(frozen=True)
class ReplayRuntimeFailure:
    """A guidance runner failed before returning findings for a replay case."""

    guidance: str
    case_id: str
    exception_type: str
    message: str


@dataclass(frozen=True)
class ReplayCaseAssessment:
    """One guidance runner's result for one replay case."""

    guidance: str
    caught_miss: bool
    findings: tuple[ReplayFinding, ...] = ()
    extra_findings: tuple[ReplayFinding, ...] = ()
    runtime_failure: Optional[ReplayRuntimeFailure] = None

    @property
    def missed_miss(self) -> bool:
        return not self.caught_miss and self.runtime_failure is None

    @property
    def blocking_failure(self) -> bool:
        return not self.caught_miss or self.runtime_failure is not None


@dataclass(frozen=True)
class ReplayCaseResult:
    """Current-vs-proposed replay comparison for one historical miss case."""

    case: ReplayCase
    current: ReplayCaseAssessment
    proposed: ReplayCaseAssessment


@dataclass(frozen=True)
class ReplayRunSummary:
    """Aggregate replay counts for one guidance runner."""

    guidance: str
    caught_misses: int
    missed_misses: int
    extra_findings: int
    runtime_failures: int
    blocking_failures: int


@dataclass(frozen=True)
class ReplayReport:
    """Comparable replay output for current guidance and proposed guidance."""

    case_results: tuple[ReplayCaseResult, ...]
    current_summary: ReplayRunSummary
    proposed_summary: ReplayRunSummary

    @property
    def proposal_publishable(self) -> bool:
        return bool(self.case_results) and self.proposed_summary.blocking_failures == 0
