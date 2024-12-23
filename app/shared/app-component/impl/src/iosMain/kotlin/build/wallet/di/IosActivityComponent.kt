package build.wallet.di

import build.wallet.feature.FeatureFlagService
import build.wallet.feature.flags.ComposeUiFeatureFlag
import build.wallet.nfc.NfcTransactor
import build.wallet.statemachine.biometric.BiometricPromptUiStateMachine
import build.wallet.statemachine.root.AppUiStateMachine
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
  val appUiStateMachine: AppUiStateMachine
  val biometricPromptUiStateMachine: BiometricPromptUiStateMachine
  val nfcTransactor: NfcTransactor
  val composUiFeatureFlag: ComposeUiFeatureFlag
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
