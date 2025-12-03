# Goose AI Rules Reference

AI rules for the Bitkey mobile app. **Read actual rule content - don't assume based on filenames alone.**

**Apply all rules below during code reviews and code generation.**

## Core Entry Points
- **ai-rules/bitkey-mobile.md** - Source-of-truth index describing the full rule catalog and mobile architecture overview
- **ai-rules/ai-code-review.md** - Cross-cutting review checklist that ties architecture, typing, testing, and tooling expectations together
- **ai-rules/context-gathering.md** - How to gather documentation and code context before editing or reviewing anything

## Architecture & Build
- **ai-rules/module-structure.md** - Module creation guidelines, directory layout, dependency rules, and code placement decisions
- **ai-rules/kmp-code-structure.md** - Kotlin Multiplatform source-set organization across shared and platform-specific code
- **ai-rules/hermit-environment.md** - Hermit environment activation; always run `. bin/activate-hermit` before any tooling
- **ai-rules/gradle-build-system.md** - Gradle usage with Hermit-provided `gradle` binary (never `./gradlew`)

## Domain & Type System
- **ai-rules/strong-typing.md** - Prefer value classes and sealed types over primitives everywhere
- **ai-rules/domain-service-pattern.md** - Domain service boundaries that replace deprecated data state machines
- **ai-rules/dao-pattern.md** - SQLDelight DAO patterns for persistence and caching
- **ai-rules/f8e-clients.md** - Network client structure, authentication, and error handling for F8e APIs
- **ai-rules/factory-pattern.md** - Runtime selection of real/fake implementations with persistable configuration

## UI State Machines
- **ai-rules/ui-state-machines.md** - High-level UI state machine architecture and responsibilities
- **ai-rules/ui-state-machines-basics.md** - Core concepts, events, effects, and simple wiring patterns
- **ai-rules/ui-state-machines-models.md** - Model shaping, reducers, and composing UI models
- **ai-rules/ui-state-machines-patterns.md** - Advanced flows, error handling, and performance considerations

## Testing
- **ai-rules/testing-overview.md** - Entry point that maps change types to the right testing strategy
- **ai-rules/testing-basics.md** - Kotest FunSpec usage, naming, and isolation rules
- **ai-rules/testing-fakes-mocks.md** - Prefer fakes, when mocks are acceptable, and how to build test doubles
- **ai-rules/testing-coroutines.md** - Turbine patterns for flows, coroutines, and async work
- **ai-rules/testing-services.md** - Domain service testing structure and dependency isolation
- **ai-rules/testing-state-machines.md** - UI state machine test harness expectations
- **ai-rules/testing-integration.md** - AppTester integration test flows and environment setup

## Meta Rules
- **ai-rules/rule-writing.md** - Standards for creating, updating, and maintaining AI rules across different AI assistants

# AI Rules Index Reference

The complete, authoritative list of all AI rules is maintained in `ai-rules/bitkey-mobile.md`. This index serves as the single source of truth for rule discovery and contains the full catalog of available rules.
