---
description: Testing UI State Machines and presentation logic
globs: ["**/*StateMachine*Tests.kt", "**/statemachine/**/*Tests.kt"]
alwaysApply: true
---

# State Machine Testing

## Summary

Testing patterns for UI State Machines that generate presentation models. Use `stateMachine.test(props)` to verify model generation, user interactions, and service coordination. Focus on behavior, not implementation.

## When to Apply

- Testing UI State Machine implementations
- Verifying model generation from props
- Testing user interaction callbacks
- Validating error state presentation

## How to Apply

### Basic State Machine Testing

```kotlin
class MobilePayStatusUiStateMachineImplTests : FunSpec({
  val mobilePayService = MobilePayServiceFake()
  val stateMachine = MobilePayStatusUiStateMachineImpl(mobilePayService)
  
  beforeTest {
    mobilePayService.reset()
  }
  
  test("shows enabled status when mobile pay is active") {
    mobilePayService.setStatus(MobilePayEnabled(limit = SpendingLimitMock))
    
    val props = MobilePayStatusProps(
      onBack = {},
      onDisable = {}
    )
    
    stateMachine.test(props) {
      awaitBody<FormBodyModel> {
        header?.headline.shouldBe("Mobile Pay Active")
        primaryButton?.text.shouldBe("Disable")
        primaryButton?.isEnabled.shouldBe(true)
      }
    }
  }
})
```

### Testing User Interactions

```kotlin
class MobilePayStatusUiStateMachineImplTests : FunSpec({
  // Define turbines at spec level
  val onDisableCalls = turbines.create<Unit>("onDisable calls")
  val onBackCalls = turbines.create<Unit>("onBack calls")
  
  val stateMachine = MobilePayStatusUiStateMachineImpl(...)
  
  test("disables mobile pay when button clicked") {
    val props = MobilePayStatusProps(
      onBack = { onBackCalls.add(Unit) },
      onDisable = { onDisableCalls.add(Unit) }
    )
    
    stateMachine.test(props) {
      awaitBody<FormBodyModel> {
        // Trigger interaction
        primaryButton?.onClick?.invoke()
      }
    }
    
    // Verify callback
    onDisableCalls.awaitItem()
  }
})
```

### Testing Service Interactions

```kotlin
test("loads spending limit on initialization") {
  val serviceMock = MobilePayServiceMock(turbines::create)
  val stateMachine = MobilePayStatusUiStateMachineImpl(serviceMock)
  
  stateMachine.test(MobilePayStatusProps()) {
    awaitBody<LoadingBodyModel>()
    
    // Verify service was called
    serviceMock.getStatusCalls.awaitItem()
  }
}
```

### Testing Error States

```kotlin
test("shows error when service fails") {
  mobilePayService.setError(NetworkError.NoConnection)
  
  stateMachine.test(props) {
    awaitBody<ErrorFormBodyModel> {
      title.shouldBe("Unable to load status")
      primaryButton?.text.shouldBe("Retry")
      errorData.shouldNotBeNull()
    }
  }
}
```

### Testing Model Transitions

```kotlin
test("transitions from loading to success") {
  val service = SlowLoadingServiceFake()
  val stateMachine = ComponentUiStateMachineImpl(service)
  
  stateMachine.test(props) {
    // Initial loading state
    awaitBody<LoadingBodyModel>()
    
    // Service completes
    service.completeLoading(testData)
    
    // Success state
    awaitBody<FormBodyModel> {
      header?.headline.shouldBe("Data Loaded")
    }
  }
}
```

### Testing Child State Machines

```kotlin
test("delegates to child state machine") {
  val childMock = ChildStateMachineMock<ChildProps>("child")
  val parent = ParentStateMachineImpl(childMock)
  
  val props = ParentProps(
    showChild = true,
    childData = testData
  )
  
  parent.test(props) {
    awaitBodyMock<ChildProps> {
      // Verify child props
      data.shouldBe(testData)
    }
  }
}
```

## Common Patterns

**Form Models:**
```kotlin
awaitBody<FormBodyModel> {
  header?.headline.shouldBe("Title")
  header?.subline.shouldBe("Description")
  primaryButton?.text.shouldBe("Continue")
  secondaryButton?.text.shouldBe("Cancel")
}
```

**Loading States:**
```kotlin
awaitBody<LoadingBodyModel> {
  message.shouldBe("Loading...")
}
```

**Error Models:**
```kotlin
awaitBody<ErrorFormBodyModel> {
  title.shouldBe("Error")
  errorData.shouldNotBeNull()
  primaryButton?.text.shouldBe("Retry")
}
```

## Example

```kotlin
class FingerprintEnrollmentUiStateMachineImplTests : FunSpec({
  // Define turbines at spec level - automatically cleaned up
  val onBackCalls = turbines.create<Unit>("onBack calls")
  val onCompleteCalls = turbines.create<EnrollmentData>("complete calls")
  
  val enrollmentService = FingerprintEnrollmentServiceFake()
  val eventTracker = EventTrackerMock(turbines::create)
  
  val stateMachine = FingerprintEnrollmentUiStateMachineImpl(
    enrollmentService = enrollmentService,
    eventTracker = eventTracker
  )
  
  beforeTest {
    enrollmentService.reset()
    // Note: turbines automatically reset - no need to reset them
  }
  
  test("shows enrollment instructions initially") {
    val props = FingerprintEnrollmentProps(
      accountId = FullAccountIdMock,
      onBack = {},
      onComplete = {}
    )
    
    stateMachine.test(props) {
      awaitBody<FormBodyModel> {
        header?.headline.shouldBe("Add Fingerprint")
        header?.subline.shouldBe("Touch the sensor to begin")
        primaryButton.shouldBeNull()  // No action until enrolled
      }
    }
  }
  
  test("completes enrollment after sufficient touches") {
    val props = FingerprintEnrollmentProps(
      accountId = FullAccountIdMock,
      onBack = { onBackCalls.add(Unit) },
      onComplete = { onCompleteCalls.add(it) }
    )
    
    stateMachine.test(props) {
      // Initial state
      awaitBody<FormBodyModel>()
      
      // Simulate touches
      repeat(5) {
        enrollmentService.simulateTouch()
      }
      
      // Completion state
      awaitBody<FormBodyModel> {
        header?.headline.shouldBe("Fingerprint Added")
        primaryButton?.text.shouldBe("Done")
        primaryButton?.onClick?.invoke()
      }
      
      // Verify callback
      val enrollment = onCompleteCalls.awaitItem()
      enrollment.fingerprintId.shouldNotBeNull()
    }
    
    // Verify tracking
    eventTracker.eventCalls
      .awaitItem()
      .action.shouldBe(ACTION_HW_FINGERPRINT_ENROLLED)
  }
})
```

## Testing Modules

**State Machine Testing:** `:libs:state-machine:testing`
**UI Testing:** `:ui:features:testing`, `:ui:framework:testing`

## Related Rules

- @ai-rules/ui-state-machines.md (state machine architecture)
- @ai-rules/testing-basics.md (core testing patterns)
- @ai-rules/testing-coroutines.md (async testing)