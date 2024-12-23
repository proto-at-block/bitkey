package build.wallet.time

import build.wallet.di.AppScope
import kotlinx.datetime.Clock
import me.tatarka.inject.annotations.Provides
import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo

@ContributesTo(AppScope::class)
interface ClockComponent {
  @Provides
  fun clock(): Clock = Clock.System
}
