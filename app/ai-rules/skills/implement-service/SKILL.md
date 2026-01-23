---
name: implement-service
description: Implement a domain service. Business logic, Result error handling, AppScope, orchestration, workflow
---

# Implement Service

## Before Starting

**Search for similar services first:**
- Search `domain/` for services in the same domain area
- Look at how similar services handle errors, dependencies, and Result types
- Check existing fakes for patterns to follow

For complex business logic or architectural decisions, review existing patterns carefully or seek guidance.

## Steps

1. Design interface in `:public` module (or `:impl` if internal-only)
2. Implement in `:impl` module with `@BitkeyInject(AppScope::class)`
3. Create fake in `:fake` module with `reset()` method
4. Write tests using fakes

Use `find-module` skill to locate the correct module.

## Rules

- Interface uses domain language, not implementation details
- All fallible operations return `Result<T, Error>` (never throw)
- Chain with `flatMap`/`mapError`, not try-catch
- No UI concerns (screen state, dialogs, navigation)
- No circular dependencies; lower-level components cannot depend on services

## References

@docs/docs/mobile/architecture/conventions.md
@docs/docs/mobile/architecture/domain-service.md
@docs/docs/mobile/architecture/domain-service-testing.md
@docs/docs/mobile/architecture/dependency-injection.md
