package build.wallet.limit

import build.wallet.di.AppScope
import me.tatarka.inject.annotations.Provides
import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo
import kotlin.jvm.JvmInline
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Defines the frequency at which we sync mobile pay status.
 */
@JvmInline
value class MobilePaySyncFrequency(val value: Duration = 30.minutes) {
  @ContributesTo(AppScope::class)
  interface Component {
    @Provides
    fun provideMobilePaySyncFrequency() = MobilePaySyncFrequency()
  }
}
