package build.wallet.statemachine.root

import build.wallet.di.AppScope
import build.wallet.time.MinimumLoadingDuration
import me.tatarka.inject.annotations.Provides
import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo

@ContributesTo(AppScope::class)
interface UiDelaysComponent {
  @Provides
  fun provideMinimumLoadingDuration() = MinimumLoadingDuration()

  @Provides
  fun provideActionSuccessDuration() = ActionSuccessDuration()

  @Provides
  fun provideWelcomeToBitkeyScreenDuration() = WelcomeToBitkeyScreenDuration()

  @Provides
  fun provideSplashScreenDelay() = SplashScreenDelay()

  @Provides
  fun provideRestoreCopyAddressStateDelay() = RestoreCopyAddressStateDelay()

  @Provides
  fun provideClearGettingStartedTasksDelay() = ClearGettingStartedTasksDelay()

  @Provides
  fun provideGettingStartedTasksAnimationDuration() = GettingStartedTasksAnimationDuration()

  @Provides
  fun provideBitkeyWordMarkAnimationDelay() = BitkeyWordMarkAnimationDelay()

  @Provides
  fun provideBitkeyWordMarkAnimationDuration() = BitkeyWordMarkAnimationDuration()
}
