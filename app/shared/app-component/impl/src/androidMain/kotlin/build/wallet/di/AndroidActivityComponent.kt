package build.wallet.di

import android.app.Activity
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import build.wallet.platform.web.InAppBrowserNavigator
import build.wallet.statemachine.biometric.BiometricPromptUiStateMachine
import build.wallet.statemachine.root.AppUiStateMachine
import build.wallet.ui.theme.ThemePreferenceService
import me.tatarka.inject.annotations.Provides
import software.amazon.lastmile.kotlin.inject.anvil.ContributesSubcomponent

/**
 * Component bound to [ActivityScope] to be used by the Android app.
 * Tied to Android app's [MainActivity] instance.
 *
 * Can be created using [AndroidActivityComponent.Factory].
 */
@SingleIn(ActivityScope::class)
@ContributesSubcomponent(ActivityScope::class)
interface AndroidActivityComponent {
  /**
   * Dependencies that are used by the Android app code.
   */
  val appUiStateMachine: AppUiStateMachine
  val biometricPromptUiStateMachine: BiometricPromptUiStateMachine
  val inAppBrowserNavigator: InAppBrowserNavigator
  val themePreferenceService: ThemePreferenceService

  @Provides
  fun activity(fragmentActivity: FragmentActivity): Activity = fragmentActivity

  /**
   * Factory for creating [AndroidAppComponent] instance:
   *
   * ```kotlin
   * val activityComponent = appComponent.activityComponent(activity, lifecycle)
   * ```
   */
  @ContributesSubcomponent.Factory(AppScope::class)
  interface Factory {
    fun activityComponent(
      fragmentActivity: FragmentActivity,
      lifecycle: Lifecycle,
    ): AndroidActivityComponent
  }
}
