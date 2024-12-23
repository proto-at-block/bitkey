package build.wallet.time

import build.wallet.di.AppScope
import me.tatarka.inject.annotations.Provides
import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo

@ContributesTo(AppScope::class)
interface DelayerComponent {
  @Provides
  fun delayer(): Delayer = Delayer.Default
}
