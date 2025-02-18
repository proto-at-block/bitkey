package build.wallet.di

import build.wallet.statemachine.root.*
import build.wallet.time.MinimumLoadingDuration
import me.tatarka.inject.annotations.Provides
import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo
import kotlin.time.Duration.Companion.seconds

/**
 * Fake UI delays used in tests.
 */
@ContributesTo(AppScope::class)
interface UiDelaysFakeComponent {
  @Provides
  fun provideMinimumLoadingDuration() = MinimumLoadingDuration(0.seconds)

  @Provides
  fun provideActionSuccessDuration() = ActionSuccessDuration(0.seconds)

  @Provides
  fun provideWelcomeToBitkeyScreenDuration() = WelcomeToBitkeyScreenDuration(0.seconds)

  @Provides
  fun provideSplashScreenDelay() = SplashScreenDelay(0.seconds)

  @Provides
  fun provideRestoreCopyAddressStateDelay() = RestoreCopyAddressStateDelay(0.seconds)

  @Provides
  fun provideClearGettingStartedTasksDelay() = ClearGettingStartedTasksDelay(0.seconds)

  @Provides
  fun provideGettingStartedTasksAnimationDuration() =
    GettingStartedTasksAnimationDuration(0.seconds)

  @Provides
  fun provideBitkeyWordMarkAnimationDelay() = BitkeyWordMarkAnimationDelay(0.seconds)

  @Provides
  fun provideBitkeyWordMarkAnimationDuration() = BitkeyWordMarkAnimationDuration(0.seconds)
}
