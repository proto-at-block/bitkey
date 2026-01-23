---
name: implement-dao
description: Implement a DAO. SQLDelight database operations, DbError, persistence, queries, fake with reset()
---

# Implement DAO

## Before Starting

**Search for similar DAOs first:**
- Search for existing `.sq` files and DAO implementations in `domain/`
- Look at how similar DAOs handle transactions, errors, and Flow emissions
- Check existing fakes for in-memory storage patterns

For SQLDelight-specific questions, consult external SQLDelight documentation.

## Steps

1. Add `.sq` file in module's `sqldelight/` directory
2. Create interface and implementation in `:impl` module
3. Add `@BitkeyInject(AppScope::class)` to implementation
4. Create fake in `:fake` module with in-memory storage and `reset()`

## Rules

- Interface and impl both in `:impl` (DAOs are never in `:public`)
- All operations return `Result<T, DbError>` or `Flow<T>`
- Write operations use `awaitTransactionWithResult()`
- Fakes use in-memory collections with `reset()` for test isolation
- DAOs consumed by services only, never by UI/state machines

## References

@docs/docs/mobile/architecture/conventions.md
@docs/docs/mobile/architecture/data-access-object.md
@docs/docs/mobile/architecture/dependency-injection.md
