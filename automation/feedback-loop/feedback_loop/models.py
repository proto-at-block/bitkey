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
ACTIONABLE_CLASSES: frozenset[PrimaryClass] = frozenset(
    {"miss", "ci_failure", "validation_failure", "post_merge_fix"}
)

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

ProposalFileChangeMode = Literal["create_or_update", "unified_diff"]

ProposalRouteRole = Literal["deterministic", "primary", "supporting"]

ResolutionState = Literal[
    "unresolved",
    "resolved_without_durable_coverage",
    "resolved_with_durable_coverage",
]


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
    facts: Optional["SignalFacts"] = None
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
    resolution: Optional["Resolution"] = None

    @property
    def is_excluded(self) -> bool:
        return self.exclusion is not None


@dataclass(frozen=True)
class SignalFacts:
    """Objective, deterministic facts about one feedback signal within its PR.

    No scores, no keyword interpretation — only structure the LLM classifier reasons over.
    """

    thread_id: str = ""
    in_reply_to_source_id: str = ""
    is_reply: bool = False
    later_reply_source_ids: tuple[str, ...] = ()
    thread_resolved: bool = False
    later_commit_source_ids: tuple[str, ...] = ()
    later_failed_check_source_ids: tuple[str, ...] = ()
    path_in_diff: bool = False
    line_in_changed_hunk: bool = False
    reviewed_head_sha: str = ""
    final_head_sha: str = ""
    reviewed_earlier_head: bool = False
    author_is_bot: bool = False
    author_trusted: bool = False


@dataclass
class Resolution:
    """LLM-judged same-PR resolution state, grounded in deterministic facts."""

    state: ResolutionState
    evidence_signal_ids: tuple[str, ...] = ()
    coverage_paths: tuple[str, ...] = ()
    rationale: str = ""


@dataclass
class Exclusion:
    """Auditable reason a feedback signal should not produce a guardrail."""

    reason: ExclusionReason
    summary: str
    summarize_as_context: bool = False
    tags: list[str] = field(default_factory=list)


@dataclass
class Cluster:
    """A group of normalized signals sharing one durable semantic theme.

    `slug` is the stable identity used for Linear memory keys; the LLM clusterer either matches
    an existing memory record's slug or mints a new one, validated deterministically.
    """

    slug: str
    signals: list[NormalizedSignal]
    title: str = ""
    area: str = ""
    severity: str = ""
    frequency: int = 0  # reconciled distinct PRs (current run merged with Linear memory history)
    current_pr_numbers: tuple[int, ...] = ()
    merged_pr_numbers: tuple[int, ...] = ()
    rank: float = 0.0
    suggested_destination: Optional[Destination] = None
    # promote | convert_to_mechanical_check | gather_more_evidence | already_covered |
    # review_only | ignore — computed deterministically after clustering.
    decision: str = ""
    matched_memory_key: str = ""
    matched_issue_identifier: str = ""
    matched_issue_url: str = ""
    rationale: str = ""
    summary: str = ""
    representative_examples: list[str] = field(default_factory=list)
    source_urls: list[str] = field(default_factory=list)

    @property
    def promotable_signals(self) -> list[NormalizedSignal]:
        return [signal for signal in self.signals if not signal.is_excluded]

    @property
    def excluded_only(self) -> bool:
        return bool(self.signals) and not self.promotable_signals

    @property
    def learning_signals(self) -> list[NormalizedSignal]:
        return [
            signal for signal in self.promotable_signals
            if signal.primary_class in ACTIONABLE_CLASSES
        ]

    @property
    def already_covered(self) -> bool:
        learning_signals = self.learning_signals
        return bool(learning_signals) and all(
            signal.resolution is not None
            and signal.resolution.state == "resolved_with_durable_coverage"
            for signal in learning_signals
        )


@dataclass(frozen=True)
class CommitFact:
    """Objective commit metadata used by the facts layer."""

    source_id: str
    sha: str
    created_at: str
    message_first_line: str


@dataclass(frozen=True)
class CheckFact:
    """Objective failed-check metadata used by the facts layer."""

    source_id: str
    name: str
    conclusion: str
    completed_at: str
    primary_class: PrimaryClass


@dataclass(frozen=True)
class PrFacts:
    """Per-PR objective facts shared by the facts layer and replay-corpus suggestions."""

    pr_number: int
    repo: str
    pr_url: str
    merged_at: str = ""
    base_sha: str = ""
    head_sha: str = ""
    merge_sha: str = ""
    changed_paths: tuple[str, ...] = ()
    commits: tuple[CommitFact, ...] = ()
    failed_checks: tuple[CheckFact, ...] = ()


@dataclass(frozen=True)
class LearningRoute:
    """One destination proposed for an LLM-extracted learning."""

    destination: Destination
    role: ProposalRouteRole
    summary: str
    rationale: str = ""
    target_artifacts: tuple[str, ...] = ()


@dataclass(frozen=True)
class Learning:
    """Durable lesson extracted from PR evidence before route-specific proposals."""

    learning_id: str
    cluster_slug: str
    evidence_urls: tuple[str, ...]
    evidence_summary: str
    agent_miss: str
    human_standard: str
    severity: Severity
    confidence: float
    affected_area: str
    routes: tuple[LearningRoute, ...] = ()


@dataclass(frozen=True)
class PlannedRoute:
    """A route-specific patch plan produced after learning extraction."""

    learning_id: str
    destination: Destination
    route_role: ProposalRouteRole
    summary: str
    target_artifacts: tuple[str, ...]
    file_changes: tuple["ProposalFileChange", ...]
    validation_commands: tuple[str, ...]
    acceptance_criteria: tuple[str, ...]
    false_positive_controls: tuple[str, ...]
    implementation_notes: str = ""
    non_goals: tuple[str, ...] = ()
    handoff_title: str = ""


@dataclass
class Proposal:
    """A minimal guardrail proposal for one cluster before PR-ready approval."""

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
    learning_id: str = ""
    route_id: str = ""
    route_role: ProposalRouteRole = "deterministic"
    linked_route_destinations: list[Destination] = field(default_factory=list)
    handoff_title: str = ""
    change_set_id: str = ""
    llm_rubric_scores: dict[str, int] = field(default_factory=dict)
    # Populated by proposal evaluation; emit() must refuse to act unless this reaches pr_ready.
    eval_passed: bool = False
    eval_state: EvalState = "proposed"
    eval_artifact: Optional["ProposalEvalArtifact"] = None
    dry_run_only: bool = True

    def __post_init__(self) -> None:
        if self.cluster.excluded_only:
            raise ValueError("excluded-only clusters cannot produce guardrail proposals")


@dataclass(frozen=True)
class ProposalEvalArtifact:
    """Evidence attached to a proposal eval decision."""

    state: EvalState
    cluster_slug: str
    rubric_markdown: str
    matched_replay_case_ids: tuple[str, ...] = ()
    blocking_reasons: tuple[str, ...] = ()
    failure_destination: EvalFailureDestination = "none"
    manual_override: str = ""
    future_pr_url: str = ""
    llm_rubric_scores: dict[str, int] = field(default_factory=dict)


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
