# AI Code Review

## Summary
Every code review or AI-generated change must verify architecture alignment, strong typing, error handling, and tests before approval. Use this rule as the umbrella checklist that ties together the more focused rules (module structure, UI state machines, testing, etc.).

## Review Workflow
1. **Gather Context First** – Skim commit/PR description and open files, then read the most relevant rules (start with `@ai-rules/bitkey-mobile.md` and `@ai-rules/context-gathering.md`).
2. **Validate Architecture** – Ensure files live in the correct module/context per `@ai-rules/module-structure.md`, `@ai-rules/kmp-code-structure.md`, and `@ai-rules/domain-service-pattern.md`.
3. **Inspect Types & Data Flow** – Confirm new APIs use value classes and sealed types from `@ai-rules/strong-typing.md`, respect DAO and service contracts, and keep platform boundaries clean.
4. **UI State Machine Audit** – When UI logic changes, apply the guidance from `@ai-rules/ui-state-machines.md` plus the basics/model/pattern supplements.
5. **Testing Requirements** – Use `@ai-rules/testing-overview.md` to pick the right test style, then verify coverage with the specific testing rules (basics, fakes, coroutines, services, state machines, integration).
6. **Tooling & Build Steps** – Confirm Hermit/Gradle usage matches `@ai-rules/hermit-environment.md` and `@ai-rules/gradle-build-system.md`.

## Checklist
- The change references existing docs/patterns instead of inventing new ones without justification.
- Files are created/modified in the correct module and shared/native source sets.
- Public interfaces expose domain types, never primitives; errors use explicit `Result` or sealed hierarchies.
- UI state machines keep business logic out of the UI layer and follow the event/model/effect split.
- Services/DAOs/factories stay single-purpose and defer platform-specific behavior to injected dependencies.
- Tests exist (or were updated) and follow Kotest style, naming, and fake-first approach; async work uses Turbine helpers.
- Command snippets and scripts include Hermit activation (`. bin/activate-hermit`) and the `gradle` wrapper expectations.
- Goose/Cursor/Claude instructions remain accurate (update the relevant files when rules change).

## Related Rules
- **@ai-rules/bitkey-mobile.md** – Source of truth index; review it before large changes.
- **@ai-rules/rule-writing.md** – Process for updating rules after the review uncovers gaps.
