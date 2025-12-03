# Testing Basics

## Summary
Core patterns: behavioral focus, `commonTest` preference, Kotest FunSpec, natural language names, prefer fakes over mocks.

## When to Apply
- Writing any test
- Setting up test infrastructure

## How to Apply

### Test Naming
Write clear sentences: `"action/subject + condition + result"`

```kotlin
// ✅ GOOD
test("returns null when user is not found")
test("creates account and sends welcome email when signup is valid")

// ❌ BAD
test("testNullCase")
test("should work correctly")
```

### Organization & Isolation
- **Prefer `commonTest`** - write once, run everywhere
- One test per scenario, explicit setup
- Reset all fakes in `beforeTest`

```kotlin
class ServiceImplTests : FunSpec({
  val dao = DaoFake()
  val service = ServiceImpl(dao)
  
  beforeTest { dao.reset() }
  
  test("generates address when none exist") {
    val result = service.generateAddress()
    result.shouldBeOk()
    result.value.index.shouldBe(0)
  }
})
```

### Assertions & Coverage
**Style:** Use method calls: `result.shouldBeOk()`, `value.shouldBe(expected)`
**Focus:** Public APIs, edge cases, error paths, integration points
**Avoid:** Private methods, trivial getters, framework functionality

### Test Doubles & Data
**Prefer fakes (95%):** `val dao = SpendingLimitDaoFake()`
**Use realistic data:** `FullAccountMock`, `BitcoinMoney.btc(0.001)`

## Example
```kotlin
class SpendingLimitServiceImplTests : FunSpec({
  val limitDao = SpendingLimitDaoFake()
  val service = SpendingLimitServiceImpl(limitDao)
  
  beforeTest { limitDao.reset() }
  
  test("sets spending limit when amount is valid") {
    val limit = SpendingLimit(BitcoinMoney.btc(0.1), SpendingPeriod.Daily)
    
    val result = service.setLimit(limit)
    
    result.shouldBeOk()
    limitDao.getActiveLimit().shouldBe(limit)
  }
  
  test("returns error when amount exceeds maximum") {
    val excessiveLimit = SpendingLimit(BitcoinMoney.btc(10.0), SpendingPeriod.Daily)
    
    val result = service.setLimit(excessiveLimit)
    
    result.shouldBeErr()
    result.error.shouldBeTypeOf<LimitError.ExceedsMaximum>()
  }
})
```

## Related Rules
- @ai-rules/testing-fakes-mocks.md (test doubles)
- @ai-rules/testing-coroutines.md (async testing)
