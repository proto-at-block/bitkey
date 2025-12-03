---
description: Core UI State Machine concepts, interface, props design, and basic patterns
globs: ["**/*StateMachine*.kt", "**/*UiStateMachine*.kt"]
alwaysApply: true
---

# UI State Machine Basics

## Summary

UI State Machines manage reactive UI logic through declarative composition using Compose Runtime. Focus on presentation/navigation logic, delegate business logic to Services. Implement `StateMachine<PropsT, ModelT>` with `@Composable model()` function.

## When to Apply

- UI presentation logic for screens/flows/widgets
- Reactive UI state, navigation, user interactions
- Hierarchical UI flows coordinating multiple screens
- Loading/error/success state management
- Complex workflows with navigation

## Core Interface

```kotlin
interface StateMachine<PropsT : Any, ModelT : Any> {
  @Composable
  fun model(props: PropsT): ModelT
}
```

- `PropsT`: External inputs driving behavior
- `ModelT`: UI presentation model (typically `ScreenModel`)
- `model()`: `@Composable` for state management

## Props Design

**Keep minimal - external inputs driving behavior:**

```kotlin
// ✅ GOOD: Minimal props
data class PaymentProps(
  val amount: BitcoinMoney,
  val onComplete: (PaymentResult) -> Unit
)

// ❌ BAD: Excessive prop drilling
data class BadProps(
  val user: User,
  val account: Account,
  val repository: Repository // Don't pass domain objects
)

// ❌ BAD: Nested props (red flag)
data class NestedBadProps(
  val userProps: UserProps,
  val accountProps: AccountProps
)
```

**Rules:** Minimize fields/callbacks, prefer Services over prop drilling, never nest props, only include data unavailable elsewhere.

## State Machine Types

**Type 1: Screen/Widget**
- Individual screens, body models, widgets
- Minimize complexity - simple UI logic
- Static-like screens with minor dynamic changes
- Use granular composable state (`remember`, `mutableStateOf`, `collectAsState`)
- State declared as individual fields

**Type 2: Orchestration**
- Stateful navigation between screens/State Machines
- Preferred for complex scenarios (loading/error/navigation)
- Use when substantial content changes
- Required for loading/error states
- Use explicit sealed class/interface hierarchies
- Minimal composable state - delegate to state classes

## State Management Patterns

**Type 1 (Screen/Widget)** - Use composable state directly:

```kotlin
@BitkeyInject(ActivityScope::class)
class SettingsUiStateMachineImpl(
  private val biometricSettingStateMachine: BiometricSettingUiStateMachine,
  private val settingsService: SettingsService,
  private val appService: AppService
) : SettingsUiStateMachine {

  @Composable
  override fun model(props: SettingsProps): ScreenModel {
    var isFieldLoading by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }
    var bottomSheet by remember { mutableStateOf<SheetModel?>(null) }
    val appStatus by remember { appService.status }.collectAsState()
    
    LaunchedEffect(props.userId) {
      isFieldLoading = true
      settingsService.validateUser(props.userId)
        .onFailure { validationError = it.message }
        .onSuccess { validationError = null }
      isFieldLoading = false
    }
    
    val bodyModel = SettingsBodyModel(
      userFieldLoading = isFieldLoading,
      validationError = validationError,
      appStatus = appStatus,
      onBiometricSettingTap = {
        bottomSheet = SheetModel(
          body = biometricSettingStateMachine.model(
            BiometricSettingProps(onComplete = { bottomSheet = null })
          ).body,
          onClosed = { bottomSheet = null }
        )
      },
      onBack = props.onBack
    )
    
    return bodyModel.asRootScreen(bottomSheetModel = bottomSheet)
  }
}
```

**Type 2 (Orchestration)** - Use explicit sealed state classes:

```kotlin
@BitkeyInject(ActivityScope::class)
class CreateAccountUiStateMachineImpl(
  private val introStateMachine: IntroUiStateMachine,
  private val codeEntryStateMachine: InviteCodeEntryUiStateMachine,
  private val backupStateMachine: CloudBackupUiStateMachine,
  private val accountService: AccountService
) : CreateAccountUiStateMachine {

  @Composable
  override fun model(props: CreateAccountProps): ScreenModel {
    var state: State by remember { mutableStateOf(State.ShowingIntroduction) }
    
    return when (val currentState = state) {
      is State.ShowingIntroduction -> introStateMachine.model(
        IntroProps(
          onContinue = { state = State.EnteringInviteCode },
          onBack = props.onBack
        )
      )
      is State.EnteringInviteCode -> codeEntryStateMachine.model(
        InviteCodeEntryProps(
          onCodeEntered = { code -> state = State.CreatingAccount(code) },
          onBack = { state = State.ShowingIntroduction }
        )
      )
      is State.CreatingAccount -> {
        LaunchedEffect(currentState.inviteCode) {
          accountService.createAccount(currentState.inviteCode)
            .onSuccess { state = State.BackingUp(it) }
            .onFailure { state = State.ShowingError(it, currentState.inviteCode) }
        }
        LoadingFormBodyModel(
          message = "Creating account...",
          onBack = { state = State.EnteringInviteCode }
        )
      }
      is State.ShowingError -> ErrorFormBodyModel(
        errorMessage = currentState.error.message ?: "Error occurred",
        onRetry = { state = State.CreatingAccount(currentState.inviteCode) },
        onBack = { state = State.EnteringInviteCode }
      )
      is State.BackingUp -> backupStateMachine.model(
        CloudBackupProps(
          account = currentState.account,
          onBackupComplete = { props.onExit(CreateAccountResult.Success(currentState.account)) },
          onBack = { state = State.CreatingAccount(currentState.account.inviteCode) },
          onSkip = { props.onExit(CreateAccountResult.Success(currentState.account)) }
        )
      )
    }
  }

  private sealed interface State {
    data object ShowingIntroduction : State
    data object EnteringInviteCode : State
    data class CreatingAccount(val inviteCode: String) : State
    data class ShowingError(val error: Throwable, val inviteCode: String) : State
    data class BackingUp(val account: Account) : State
  }
}
```

## Best Practices

**Type 1 (Screen/Widget):**
- ✅ Multiple `remember { mutableStateOf() }` for UI concerns
- ✅ `collectAsState()` for reactive streams
- ✅ Simple state declarations
- ✅ Ideal for forms, settings, static content
- ❌ Avoid for major loading/error states

**Type 2 (Orchestration):**
- ✅ Explicit `private sealed interface/class` for states
- ✅ Descriptive state names for workflow steps
- ✅ Include necessary data in state classes
- ✅ Minimal composable state - single state variable
- ✅ `when` expressions for exhaustive handling
- ✅ Inject/delegate to other State Machines
- ✅ Proper back navigation via state transitions
- ✅ `State` definition at class bottom
- ✅ Prefer for loading/error/success management
- ❌ Never expose internal state classes
- ❌ Focus on simplicity over reusability

## Organization

**State Definition:** Place `State` sealed interface/class at bottom for readability. Use simple name `State`.

**Back Navigation:**
- **Internal**: Transition between states (`state = State.PreviousState`)
- **External**: Use `props.onBack` to exit flow
- **Context-aware**: Different states have different back destinations
- **Data preservation**: Include necessary data in transitions

## Injection and Flow Breakdown

**Both types inject other State Machines:**
- Type 1: For sheets, dialogs, sub-components
- Type 2: For workflow steps and navigation

**Guidelines:** Optimize for simplicity, break down complex flows, consolidate when multiple add indirection, fewer State Machines = less complexity.

## Callback Patterns

**onBack vs onExit/onResult:**

```kotlin
data class CreateAccountProps(
  val onBack: () -> Unit,           // User backed from first screen
  val onExit: (CreateAccountResult) -> Unit  // Flow finished
)

sealed interface CreateAccountResult {
  data class Success(val account: Account) : CreateAccountResult
  data class Error(val error: Throwable) : CreateAccountResult
}
```

- **`onBack`**: User cancelled from initial screen - flow never progressed
- **`onExit`/`onResult`**: Flow completed - parent needs result
- **Same callback**: When parent doesn't distinguish cancellation vs completion

## Service Integration

**Boundaries:**
- ✅ Use Domain Services and utilities
- ❌ Avoid F8e clients, networking, database calls
- Services handle threading - State Machines delegate business logic

**Patterns:**
```kotlin
// ✅ GOOD: Access Service properties/flows
@Composable 
override fun model(props: Props): ScreenModel {
  val data by remember { dataService.dataFlow }.collectAsState()
  val status by remember { statusService.currentStatus }.collectAsState()
  
  LaunchedEffect(props.triggerRefresh) {
    if (props.triggerRefresh) {
      dataService.refreshData() // Suspend call in effect
    }
  }
}

// ❌ BAD: Direct method calls
val data by dataService.getData().collectAsState() // Don't call methods
val result = dataService.processData(props.input) // Wrong - direct call
```

**Guidelines:**
- Property access: `dataService.currentData.collectAsState()`
- Method Flow: `remember(filter) { dataService.getFilteredData(filter) }.collectAsState()`
- Suspend calls in `LaunchedEffect`

## Dependency Injection

**Use `ActivityScope` for all UI State Machines:**

```kotlin
@BitkeyInject(ActivityScope::class)
class PaymentUiStateMachineImpl(
  private val paymentService: PaymentService,
  private val accountService: AccountService
) : PaymentUiStateMachine
```

**Why:** Sub-scope of AppScope for Activity lifecycle. NFC components depend on Activity instance, many State Machines rely on NfcTagScanner.

## Navigation

**Use declarative navigation:**

```kotlin
// ✅ GOOD: Declarative
return PaymentScreenModel(
  navigationTarget = when (paymentState) {
    PaymentState.Success -> NavigationTarget.Home
    else -> null
  }
)

// ❌ BAD: Imperative
navigator.navigateToHome() // Don't inject Navigator
```

**Rules:** Don't inject Navigator, emit navigation state through models, trigger parent state changes.

## Simple Models vs State Machines

**Use simple data classes for static screens:**

```kotlin
// ✅ GOOD: Simple model for static content
data class AboutScreenModel(
  val appVersion: String,
  val buildNumber: String,
  val onContactSupport: () -> Unit,
  val onPrivacyPolicy: () -> Unit
) : ScreenModel

// ❌ BAD: Unnecessary State Machine
class AboutUiStateMachine : StateMachine<AboutUiProps, ScreenModel> {
  @Composable
  override fun model(props: AboutUiProps): ScreenModel {
    return AboutScreenModel(...) // No reactive state/side effects
  }
}
```

**Use simple models when:** Static data with callbacks, no service calls/side effects, no state management, pure presentation.

## Related Rules

- @ai-rules/ui-state-machines-models.md (model types)
- @ai-rules/ui-state-machines-patterns.md (advanced techniques)
- @ai-rules/domain-service-pattern.md (business logic)
- @ai-rules/strong-typing.md (type definitions)
- @ai-rules/module-structure.md (placement)