# Testing Overview

## Summary
Entry point for testing guidelines. Tests: behavioral, isolated, `commonTest` preferred, 95% fakes, turbines for async.

## Philosophy
- **Write once, run everywhere:** `commonTest` preference
- **Behavioral focus:** Test what, not how
- **Fakes over mocks:** 95% fakes, 5% mocks for interaction verification
- **Complete isolation:** Hermetic tests via `reset()` methods
- **Natural language:** Sentence-like test names

## Quick Reference
| Component | Rule | Focus |
|-----------|------|-------|
| Services | @ai-rules/testing-services.md | Business logic |
| State Machines | @ai-rules/testing-state-machines.md | UI logic |
| DAOs | testing-basics.md | Data persistence |
| Async/Flows | @ai-rules/testing-coroutines.md | Turbine patterns |
| Integration | @ai-rules/testing-integration.md | AppTester |

## Testing Rules
- **@ai-rules/testing-basics.md** - Core patterns, naming, isolation
- **@ai-rules/testing-fakes-mocks.md** - Test doubles (prefer fakes)
- **@ai-rules/testing-coroutines.md** - Async operations, turbines

## Example
```kotlin
class ServiceImplTests : FunSpec({
  val dao = SpendingLimitDaoFake()
  val service = ServiceImpl(dao)
  
  beforeTest { dao.reset() }
  
  test("enables pay when valid limit provided") {
    val limit = SpendingLimit(BitcoinMoney.btc(0.1))
    
    val result = service.enablePay(limit)
    
    result.shouldBeOk()
    dao.getActiveLimit().shouldBe(limit)
  }
})
```

## Related Rules
- @ai-rules/domain-service-pattern.md (service architecture)
- @ai-rules/ui-state-machines.md (state machine architecture)
