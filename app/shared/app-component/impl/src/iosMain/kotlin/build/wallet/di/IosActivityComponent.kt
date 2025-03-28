package build.wallet.di

import build.wallet.ComposeIosAppUIController
import build.wallet.feature.FeatureFlagService
import build.wallet.nfc.NfcTransactor
import build.wallet.statemachine.biometric.BiometricPromptUiStateMachine
import software.amazon.lastmile.kotlin.inject.anvil.ContributesSubcomponent

/**
 * Component bound to [ActivityScope] to be used by the iOS app.
 * Unlike Android, on iOS this component will technically have the same
 * scoping as the [IosAppComponent].
 *
 * Can be created using [IosActivityComponent.Factory].
 */
@SingleIn(ActivityScope::class)
@ContributesSubcomponent(ActivityScope::class)
interface IosActivityComponent {
  /**
   * Dependencies that are used by iOS Swift code.
   */
  val composeAppUIController: ComposeIosAppUIController
  val biometricPromptUiStateMachine: BiometricPromptUiStateMachine
  val nfcTransactor: NfcTransactor
  val featureFlagService: FeatureFlagService

  /**
   * Factory for creating [IosActivityComponent] instance:
   *
   * ```kotlin
   * val activityComponent = appComponent.activityComponent()
   * ```
   */
  @ContributesSubcomponent.Factory(AppScope::class)
  interface Factory {
    fun activityComponent(): IosActivityComponent
  }
}
