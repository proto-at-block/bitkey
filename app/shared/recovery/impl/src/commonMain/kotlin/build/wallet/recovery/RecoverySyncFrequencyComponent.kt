package build.wallet.recovery

import build.wallet.di.AppScope
import me.tatarka.inject.annotations.Provides
import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo
import kotlin.time.Duration.Companion.minutes

@ContributesTo(AppScope::class)
interface RecoverySyncFrequencyComponent {
  @get:Provides
  val frequency get() = RecoverySyncFrequency(1.minutes)
}
