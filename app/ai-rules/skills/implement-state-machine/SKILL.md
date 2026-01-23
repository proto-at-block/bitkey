---
name: implement-state-machine
description: Implement a UI state machine. ScreenModel, presentation, Type1/Type2, Props, ActivityScope, navigation
---

# Implement State Machine

## Before Starting

**Search for similar state machines first.** This codebase has many examples:
- Search for state machines in the same domain/feature area
- Look at how similar UI flows handle loading, errors, navigation
- Check existing models in `ui/features/` for reusable patterns

For unfamiliar Compose or KMP patterns, consult external documentation.

## Steps

1. Choose pattern: Type 1 (simple UI) or Type 2 (loading/error/multi-step)
2. Create props with minimal fields
3. Implement `model()` function returning ScreenModel
4. Use pre-built models when possible, else FormBodyModel
5. Write tests with state machine test helpers

## Rules

- Never call network/DB/F8e directly—use services
- Navigation via models only, never imperative
- Props: no domain objects, no nesting, no suspend callbacks
- Use `@BitkeyInject(ActivityScope::class)`
- Type 2: use `private sealed interface State` at class bottom

## References

@docs/docs/mobile/architecture/conventions.md
@docs/docs/mobile/architecture/state-machines.md
@docs/docs/mobile/architecture/state-machine-models.md
@docs/docs/mobile/architecture/state-machine-patterns.md
@docs/docs/mobile/architecture/state-machine-testing.md
@docs/docs/mobile/architecture/ui.md
