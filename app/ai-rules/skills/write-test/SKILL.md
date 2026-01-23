---
name: write-test
description: Write tests. Kotest FunSpec, fakes over mocks, Turbine for Flows, unit and integration testing
---

# Write Test

## Before Starting

**Search for similar tests first—this is critical:**
- Search for tests of similar components (services, state machines, DAOs)
- Look at how fakes are set up and reset
- Check test naming conventions and assertion patterns

For Kotest or Turbine questions, consult external documentation.

## Steps

1. Find similar existing test as template (search broadly)
2. Create test class extending `FunSpec`
3. Define fakes at spec level, reset in `beforeTest`
4. Write behavior-oriented test names

## Rules

- Fakes over mocks (95% of cases)
- Reset fakes in `beforeTest { fake.reset() }`
- Test behavior, not implementation (verify outcomes, not calls)
- Mocks only for interaction verification (analytics, audit logs)
- Flow testing: `flow.test { awaitItem() }`
- Mock verification: `mock.calls.awaitItem()` directly (no `.test {}`)
- Avoid `expect*` methods—they're non-deterministic

## Running

```bash
gradle :module:jvmTest --tests "*.MyTests"     # Unit
gradle jvmIntegrationTest --tests "*.MyTests"  # Functional
```

## References

@docs/docs/mobile/architecture/conventions.md
@docs/docs/mobile/testing/overview.md
@docs/docs/mobile/testing/coroutines.md
@docs/docs/mobile/architecture/domain-service-testing.md
@docs/docs/mobile/architecture/state-machine-testing.md
