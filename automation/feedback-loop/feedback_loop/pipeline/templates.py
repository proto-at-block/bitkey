"""Promotion templates for guardrail proposals.

One template per destination in the promotion matrix. The LLM route planner produces the concrete
patch plans; these templates supply destination titles, required section structure, and reviewer
guidance used when rendering proposals.
"""

from __future__ import annotations

from dataclasses import dataclass

from ..models import Destination


@dataclass(frozen=True)
class TemplateSection:
    """One required section in a human-reviewable promotion proposal."""

    key: str
    title: str
    instructions: str


@dataclass(frozen=True)
class PromotionTemplate:
    """Destination-specific guidance for guardrail proposal generation."""

    destination: Destination
    title: str
    target_artifacts: tuple[str, ...]
    applies_when: str
    sections: tuple[TemplateSection, ...]
    decision_rules: tuple[str, ...] = ()

    def section_keys(self) -> set[str]:
        return {section.key for section in self.sections}


EVIDENCE_SECTION = TemplateSection(
    key="evidence",
    title="Evidence",
    instructions=(
        "Summarize the cluster in your own words. Include source IDs and source URLs. "
        "Do not paste raw PR comments verbatim when a concise summary is enough."
    ),
)

EXAMPLES_SECTION = TemplateSection(
    key="examples",
    title="Examples",
    instructions=(
        "Show the smallest representative before/after or fixture example needed for review. "
        "Prefer synthetic examples when the source links already preserve the original discussion."
    ),
)

REQUIRED_SECTION_KEYS = frozenset(
    {
        "evidence",
        "scope",
        "examples",
        "non_goals",
        "validation_steps",
        "reviewer_instructions",
        "rollback_guidance",
    }
)


def _sections(
    *,
    scope: str,
    non_goals: str,
    validation_steps: str,
    reviewer_instructions: str,
    rollback_guidance: str,
) -> tuple[TemplateSection, ...]:
    return (
        EVIDENCE_SECTION,
        TemplateSection("scope", "Scope", scope),
        EXAMPLES_SECTION,
        TemplateSection("non_goals", "Non-goals", non_goals),
        TemplateSection("validation_steps", "Validation steps", validation_steps),
        TemplateSection("reviewer_instructions", "Reviewer instructions", reviewer_instructions),
        TemplateSection("rollback_guidance", "Rollback guidance", rollback_guidance),
    )


PROMOTION_TEMPLATES: tuple[PromotionTemplate, ...] = (
    PromotionTemplate(
        destination="test_or_linter",
        title="Test or linter follow-up",
        target_artifacts=("tests/", "linter configuration", "validation fixtures"),
        applies_when=(
            "The cluster describes a deterministic rule that can be enforced by code, a test, "
            "or an existing lint/validation tool."
        ),
        sections=_sections(
            scope=(
                "Name the exact module, language, and behavior boundary the check covers. "
                "Keep the check narrower than the observed evidence unless the owner approves "
                "a broader rule."
            ),
            non_goals=(
                "Do not add prose-only guidance for mechanically checkable feedback. Do not "
                "tighten unrelated lint rules or broaden fixtures beyond the cluster theme."
            ),
            validation_steps=(
                "List the focused test/lint command. When practical, include the failing "
                "pre-fix case and the passing post-fix command."
            ),
            reviewer_instructions=(
                "Ask the owning reviewer to confirm the check is deterministic, low-noise, "
                "and scoped to the recurring miss."
            ),
            rollback_guidance=(
                "Revert the check or narrow the fixture/rule if it produces false positives "
                "or blocks unrelated valid changes."
            ),
        ),
    ),
    PromotionTemplate(
        destination="agents_check",
        title=".agents/checks guardrail",
        target_artifacts=(".agents/checks/<check-name>", "check fixtures", "check documentation"),
        applies_when=(
            "The cluster is a deterministic repo-level review guardrail that is not a normal "
            "language linter or unit test."
        ),
        sections=_sections(
            scope=(
                "Define the file patterns, inputs, and exact pass/fail condition. Link the "
                "canonical policy or doc the check enforces."
            ),
            non_goals=(
                "Do not encode subjective reviewer preferences, product decisions, or "
                "rules that require domain judgment at runtime."
            ),
            validation_steps=(
                "Include positive and negative fixtures plus the command that runs the check "
                "locally or in CI."
            ),
            reviewer_instructions=(
                "Ask reviewers to inspect fixture coverage and confirm the check can be "
                "maintained by the owning area."
            ),
            rollback_guidance=(
                "Disable or remove the check and keep the fixture that demonstrates the false "
                "positive so a narrower replacement can be proposed."
            ),
        ),
    ),
    PromotionTemplate(
        destination="ai_skill",
        title=".ai skill update",
        target_artifacts=(".ai/skills/<skill>/SKILL.md", "skill references", "generated agent context"),
        applies_when=(
            "The cluster describes a reusable, multi-step procedure an agent should follow "
            "when a specific trigger appears."
        ),
        sections=_sections(
            scope=(
                "State the trigger, the actors, and the repository areas the procedure applies "
                "to. Prefer updating an existing skill over creating a new one."
            ),
            non_goals=(
                "Do not put one-off facts, broad always-on rules, or human-facing rationale in "
                "a skill. Link to docs instead of duplicating them."
            ),
            validation_steps=(
                "Run the AI context generator/check when generated agent files are affected, "
                "and include a small dry-run example of the skill trigger."
            ),
            reviewer_instructions=(
                "Ask reviewers to confirm the skill trigger is precise and the procedure is "
                "safe to apply repeatedly."
            ),
            rollback_guidance=(
                "Revert the skill change and regenerate agent context if the trigger is too "
                "broad or the procedure causes wrong routing."
            ),
        ),
    ),
    PromotionTemplate(
        destination="ai_agents_md",
        title=".ai/AGENTS.md scoped rule",
        target_artifacts=(".ai/AGENTS.md", "app/.ai/AGENTS.md", "server/.ai/AGENTS.md", "firmware/.ai/AGENTS.md"),
        applies_when=(
            "The cluster is a short, always-apply convention for agents and should live at "
            "the narrowest repository scope that covers the evidence."
        ),
        sections=_sections(
            scope=(
                "Name the narrowest applicable `.ai/AGENTS.md` source and the generated files "
                "that must be refreshed."
            ),
            non_goals=(
                "Do not duplicate human-facing rationale from docs or encode a multi-step "
                "workflow that belongs in a skill."
            ),
            validation_steps=(
                "Run `./tools/ai-context/ai-context-generate.sh` and "
                "`./tools/ai-context/ai-context-check.sh` when generated context changes."
            ),
            reviewer_instructions=(
                "Ask the owner of the scoped AGENTS source to confirm the rule is always-on, "
                "short, and not better handled mechanically."
            ),
            rollback_guidance=(
                "Revert the `.ai/AGENTS.md` source change and regenerate context to restore "
                "the previous generated files."
            ),
        ),
    ),
    PromotionTemplate(
        destination="docs",
        title="Docs update",
        target_artifacts=("docs/docs/", "module README.md", ".ai links to canonical docs"),
        applies_when=(
            "The cluster captures human-facing knowledge, rationale, architecture, runbook, "
            "or glossary content that should be canonical in documentation."
        ),
        sections=_sections(
            scope=(
                "Name the canonical doc or README and any `.ai` source that should link to it. "
                "Keep one source of truth for the topic."
            ),
            non_goals=(
                "Do not copy the same guidance into multiple docs. Do not use docs as a "
                "substitute for an enforceable test or check."
            ),
            validation_steps=(
                "Run any relevant docs checks and verify links, anchors, and generated agent "
                "context when `.ai` links change."
            ),
            reviewer_instructions=(
                "Ask the domain owner to confirm the doc is the canonical home and the "
                "rationale is accurate."
            ),
            rollback_guidance=(
                "Revert the doc change and any `.ai` cross-link if the guidance is superseded "
                "or moved to a more specific source."
            ),
        ),
    ),
    PromotionTemplate(
        destination="world_model",
        title="World model promotion",
        target_artifacts=("world model store", "Linear research issue until the store exists"),
        applies_when=(
            "The cluster captures durable domain facts agents should reason over, especially "
            "when the knowledge spans repository boundaries and is not an instruction."
        ),
        sections=_sections(
            scope=(
                "State the domain fact, the systems it spans, and the owning knowledge source. "
                "If the world model store is unavailable, hold the proposal as research-only."
            ),
            non_goals=(
                "Do not store agent instructions, procedures, or repo-local conventions as "
                "world model facts. Route those to skills, AGENTS, docs, checks, or tests."
            ),
            validation_steps=(
                "Identify the authoritative owner and source document. Validate the fact "
                "against at least one source outside the triggering PR when it spans repos."
            ),
            reviewer_instructions=(
                "Ask the domain owner to confirm the fact is stable, reusable, and appropriate "
                "for cross-repo reasoning."
            ),
            rollback_guidance=(
                "Remove or mark the fact stale if ownership changes, the source is superseded, "
                "or later evidence narrows it to a repo-local rule."
            ),
        ),
        decision_rules=(
            "Use only for durable facts, not instructions or workflows.",
            "Use when the knowledge spans repo boundaries or belongs to a shared domain model.",
            "If the world model store is not available, create no artifact PR; keep a research issue.",
        ),
    ),
)

_TEMPLATES_BY_DESTINATION = {template.destination: template for template in PROMOTION_TEMPLATES}

VALIDATION_COMMANDS_BY_DESTINATION: dict[Destination, tuple[str, ...]] = {
    "test_or_linter": (
        "Run the focused test or linter command for the touched module.",
        "Capture the failing replay case before the guardrail and the passing result after it.",
    ),
    "agents_check": (
        "Run the new `.agents/checks` guardrail against positive and negative fixtures.",
        "Run the repository command that wires the check into agent/CI validation.",
    ),
    "ai_skill": (
        "Run the AI context generator/check if generated agent context changes.",
        "Dry-run the skill trigger against one replay case from this cluster.",
    ),
    "ai_agents_md": (
        "Run `./tools/ai-context/ai-context-generate.sh`.",
        "Run `./tools/ai-context/ai-context-check.sh`.",
    ),
    "docs": (
        "Run relevant docs/link checks for the touched documentation.",
        "Verify any `.ai` source that links to the doc still regenerates cleanly.",
    ),
    "world_model": (
        "Validate the fact against the owning source before promoting it.",
        "Record the replay case that should retrieve or exercise the world model fact.",
    ),
}


def _assert_destination_coverage() -> None:
    template_destinations = set(_TEMPLATES_BY_DESTINATION)
    validation_destinations = set(VALIDATION_COMMANDS_BY_DESTINATION)
    if template_destinations == validation_destinations:
        return

    missing_validation = sorted(template_destinations - validation_destinations)
    extra_validation = sorted(validation_destinations - template_destinations)
    raise RuntimeError(
        "promotion destination coverage mismatch: "
        f"missing validation commands for {missing_validation}; "
        f"extra validation commands for {extra_validation}"
    )


_assert_destination_coverage()


def promotion_templates() -> tuple[PromotionTemplate, ...]:
    """Return all promotion templates in promotion-matrix order."""
    return PROMOTION_TEMPLATES


def template_for_destination(destination: Destination) -> PromotionTemplate:
    """Return the template for one promotion destination."""
    return _TEMPLATES_BY_DESTINATION[destination]
