package build.wallet.di

/**
 * Sub-scope of [AppScope] primarily designed to accommodate Android framework.
 *
 * [ActivityScope] exists to support the Android `Activity` lifecycle, where `Activity` instances and their UIs
 * can be recreated multiple times (e.g., during configuration changes). In our codebase, essential NFC components
 * such as `AndroidNfcTagScanner` depend on the latest `Activity` instance. Many UI State Machines rely on
 * `NfcTagScanner`, binding all UI State Machines to `ActivityScope`.
 *
 * On iOS and JVM, `AppScope` and `ActivityScope` are effectively the same, but we still contribute UI State Machines
 * and other `commonMain` implementations to `ActivityScope` as needed for consistency across platforms.
 *
 * As a rule of thumb all UI State Machine implementations should be contributed to [ActivityScope].
 *
 * Usage:
 * ```kotlin
 * @BitkeyInject(ActivityScope::class)
 * class SomeUiStateMachineImpl : SomeUiStateMachine { }
 *
 * // This allows `SomeUiStateMachineImpl` to be injected where `SomeUiStateMachine` is required within `ActivityScope`.
 * ```
 */
@SingleIn(scope = AppScope::class)
abstract class ActivityScope private constructor()
