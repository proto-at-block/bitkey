---
description: Advanced UI State Machine patterns including error handling, retry logic, analytics, and performance optimization
globs: ["**/*StateMachine*.kt", "**/*UiStateMachine*.kt"]
alwaysApply: true
---

# UI State Machine Advanced Patterns

## Summary

Advanced patterns for error handling, retry logic, performance optimization, analytics integration, and complex side effects in UI State Machines. Enables robust, production-ready implementations.

## Remember Key Management

**Control expensive calculations with proper keys:**

```kotlin
// ❌ BAD: Recalculates every recomposition
val result = remember { expensiveCalculation(props.value1, props.value2) }

// ✅ GOOD: Only recalculates when dependencies change
val result = remember(props.value1, props.value2) {
  expensiveCalculation(props.value1, props.value2)
}
val data by remember { service.dataStream }.collectAsState()
val convertedData by remember(converter, inputData) {
  converter.convertStream(inputData)
}.collectAsState(initialValue)
```

**Key guidelines:** Include all external values affecting computation (1-4 keys max).

## Side Effects

**Use `rememberStableCoroutineScope()` (prevents callback recomposition):**

```kotlin
@Composable
override fun model(props: PaymentProps): ScreenModel {
  val scope = rememberStableCoroutineScope()
  return PaymentBodyModel(
    onProcessPayment = { request ->
      scope.launch { paymentService.processPayment(request) }
    }
  )
}
```

**Two patterns:**
- **LaunchedEffect**: Prop-driven effects, auto-cancellation, debouncing
- **rememberCoroutineScope**: User-action callbacks

```kotlin
// LaunchedEffect for deterministic behavior
LaunchedEffect(props.autoProcess) {
  if (props.autoProcess != null) {
    paymentService.processPayment(props.autoProcess)
  }
}

// rememberCoroutineScope for callbacks
val scope = rememberCoroutineScope()
onProcessPayment = { scope.launch { paymentService.processPayment(it) } }
```

## Error Handling Patterns

**Use Type 2 with explicit error states:**

```kotlin
private sealed interface State {
  data object Loading : State
  data class Success(val data: Account) : State
  data class Error(val errorData: ErrorData) : State
}

@Composable
override fun model(props: CreateAccountProps): ScreenModel {
  var state: State by remember { mutableStateOf(State.Loading) }
  
  LaunchedEffect(props.inviteCode) {
    accountService.createAccount(props.inviteCode)
      .onSuccess { state = State.Success(it) }
      .onFailure { state = State.Error(ErrorData(...)) }
  }
  
  return when (val currentState = state) {
    is State.Loading -> LoadingBodyModel("Creating account...")
    is State.Success -> SuccessBodyModel(currentState.data)
    is State.Error -> ErrorFormBodyModel(
      title = "Failed", onRetry = { state = State.Loading }
    )
  }
}
```

**Best practices:** Separate models per state, user-friendly messages, recovery paths, use `ErrorData`.

## Retry Patterns

**Use boolean flags for retry:**

```kotlin
@Composable
override fun model(props: ServiceProps): ScreenModel {
  var isRequesting by remember { mutableStateOf(true) }
  var state: State by remember { mutableStateOf(State.Loading) }
  
  if (isRequesting) {
    LaunchedEffect(Unit) {
      serviceCall()
        .onSuccess { state = State.Success(it); isRequesting = false }
        .onFailure { state = State.Error(it); isRequesting = false }
    }
  }
  
  return when (val currentState = state) {
    is State.Error -> ErrorFormBodyModel(
      onRetry = { isRequesting = true },
      onBack = props.onBack
    ).asModalScreen()
  }
}
```

**Pattern:** Boolean flags outside `LaunchedEffect`, set `true` to retry, `false` when complete.

## Analytics and Screen Tracking

**Required for FormBodyModel:**

```kotlin
data class SettingsFormBodyModel(...) : FormBodyModel(
  id = EventTrackerScreenId.SETTINGS_SCREEN,
  eventTrackerContext = SettingsEventTrackerContext.ACCOUNT,
  eventTrackerShouldTrack = true, // Default true
)
```

**Use `EventTrackerScreenId` enum, optional `EventTrackerContext`, disable with `eventTrackerShouldTrack = false`.**

## Performance Optimization

```kotlin
// Cache expensive calculations
val processedData = remember(props.rawData, props.filters) {
  expensiveDataProcessing(props.rawData, props.filters)
}

// Lazy computation
val result = remember(props.shouldCalculate, props.inputData) {
  if (props.shouldCalculate) expensiveCalculation(props.inputData) else null
}

// Debounce user input
LaunchedEffect(props.searchQuery) {
  delay(300)
  if (props.searchQuery.isNotEmpty()) searchService.search(props.searchQuery)
}
```

**Guidelines:** Cache with keys, lazy evaluation, debounce input, early returns, stable references.

## Complex Flow Management

**Multi-step workflow pattern:**

```kotlin
@BitkeyInject(ActivityScope::class)
class PaymentFlowUiStateMachineImpl(
  private val amountEntryStateMachine: AmountEntryUiStateMachine,
  private val recipientStateMachine: RecipientSelectionUiStateMachine,
  private val confirmationStateMachine: PaymentConfirmationUiStateMachine,
  private val paymentService: PaymentService
) : PaymentFlowUiStateMachine {

  @Composable
  override fun model(props: PaymentFlowProps): ScreenModel {
    var state: State by remember { mutableStateOf(State.EnteringAmount) }
    
    return when (val currentState = state) {
      is State.EnteringAmount -> amountEntryStateMachine.model(
        AmountEntryProps(
          onAmountEntered = { state = State.SelectingRecipient(it) },
          onBack = props.onExit
        )
      )
      is State.SelectingRecipient -> recipientStateMachine.model(
        RecipientSelectionProps(
          amount = currentState.amount,
          onRecipientSelected = { 
            state = State.ConfirmingPayment(currentState.amount, it)
          },
          onBack = { state = State.EnteringAmount }
        )
      )
      is State.ProcessingPayment -> {
        LaunchedEffect(currentState.amount, currentState.recipient) {
          paymentService.processPayment(currentState.amount, currentState.recipient)
            .onSuccess { props.onPaymentComplete(it) }
            .onFailure { state = State.PaymentError(it, currentState.amount, currentState.recipient) }
        }
        LoadingFormBodyModel("Processing...").asModalScreen()
      }
      // ... other states
    }
  }

  private sealed interface State {
    data object EnteringAmount : State
    data class SelectingRecipient(val amount: BitcoinMoney) : State
    data class ProcessingPayment(val amount: BitcoinMoney, val recipient: String) : State
    data class PaymentError(val error: Throwable, val amount: BitcoinMoney, val recipient: String) : State
  }
}
```

## Advanced State Management

**State persistence:**
```kotlin
var formData by rememberSaveable(stateSaver = FormDataStateSaver) { 
  mutableStateOf(FormData.empty())
}
```

**Derived validation:**
```kotlin
val validationErrors = remember(inputState) {
  buildList {
    if (inputState.email.isBlank()) add("Email required")
    if (!inputState.email.contains("@")) add("Invalid email")
  }.toImmutableList()
}
val isFormValid = remember(validationErrors) { validationErrors.isEmpty() }
```

## Testing Considerations

**Testable patterns:**
```kotlin
@BitkeyInject(ActivityScope::class)
class TestableUiStateMachineImpl(
  private val dataService: DataService,
  private val validationService: ValidationService
) : TestableUiStateMachine {
  
  @Composable
  override fun model(props: TestableProps): ScreenModel {
    val data by remember { dataService.dataStream }.collectAsState()
    var isLoading by remember { mutableStateOf(false) }
    
    LaunchedEffect(props.triggerValidation) {
      if (props.triggerValidation) {
        isLoading = true
        validationService.validate(data)
        isLoading = false
      }
    }
    
    return TestableBodyModel(
      data = data?.displayValue ?: "No data",
      isLoading = isLoading
    ).asRootScreen()
  }
}
```

**Best practices:** Inject dependencies, simple state logic, predictable side effects, clear Props.

## Advanced Error Recovery

**Progressive error handling with auto-retry:**
```kotlin
@Composable
override fun model(props: RobustProps): ScreenModel {
  var state: State by remember { mutableStateOf(State.Loading) }
  var retryCount by remember { mutableStateOf(0) }
  
  LaunchedEffect(state, retryCount) {
    when (state) {
      is State.Loading -> {
        try {
          val result = dataService.fetchCriticalData()
          state = State.Success(result); retryCount = 0
        } catch (error: Exception) {
          when {
            retryCount < 3 -> {
              delay(1000 * (retryCount + 1)); retryCount++
            }
            error is NetworkException -> state = State.NetworkError(error)
            else -> state = State.CriticalError(error)
          }
        }
      }
    }
  }
  
  return when (val currentState = state) {
    is State.Loading -> LoadingFormBodyModel(
      if (retryCount > 0) "Retrying... ($retryCount/3)" else "Loading..."
    )
    is State.NetworkError -> ErrorFormBodyModel(
      title = "Connection Problem",
      onRetry = { state = State.Loading; retryCount = 0 }
    )
  }
}
```

## Logging Guidelines

**Minimal UI State Machine logging** - delegate to Services and domain components.

## Complete Example

```kotlin
// Type 2 State Machine for loading/error/success states
@BitkeyInject(ActivityScope::class)
class PaymentUiStateMachineImpl(
  private val paymentService: PaymentService,
  private val accountService: AccountService
) : PaymentUiStateMachine {
  
  @Composable
  override fun model(props: PaymentProps): ScreenModel {
    var state: State by remember { mutableStateOf(State.Loading) }
    
    LaunchedEffect(Unit) {
      accountService.getActiveAccount()
        .onSuccess { state = State.Ready(it) }
        .onFailure { state = State.Error(it) }
    }
    
    return when (val currentState = state) {
      is State.Loading -> LoadingFormBodyModel("Loading...").asModalScreen()
      is State.Error -> ErrorFormBodyModel(
        title = "Failed to load",
        onRetry = { state = State.Loading },
        onBack = props.onBack
      ).asModalScreen()
      is State.Ready -> PaymentFormBodyModel(
        amount = props.amount,
        onSubmit = { state = State.Processing(currentState.account) },
        onBack = props.onBack
      ).asModalScreen()
      is State.Processing -> {
        LaunchedEffect(currentState.account) {
          paymentService.processPayment(props.amount, props.recipient, currentState.account)
            .onSuccess { props.onSuccess() }
            .onFailure { state = State.ProcessingError(it, currentState.account) }
        }
        LoadingFormBodyModel("Processing...").asModalScreen()
      }
    }
  }
  
  private sealed interface State {
    data object Loading : State
    data class Error(val error: Throwable) : State
    data class Ready(val account: Account) : State
    data class Processing(val account: Account) : State
    data class ProcessingError(val error: Throwable, val account: Account) : State
  }
}
```

## Related Rules

- @ai-rules/ui-state-machines-basics.md (for core concepts and fundamental patterns)
- @ai-rules/ui-state-machines-models.md (for model design and presentation)
- @ai-rules/domain-service-pattern.md (for business logic delegation)
- @ai-rules/strong-typing.md (for error handling and Result types)