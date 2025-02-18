package build.wallet.recovery.sweep

import build.wallet.di.AppScope
import me.tatarka.inject.annotations.Provides
import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo
import kotlin.jvm.JvmInline
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Defines how frequently we should check for sweeps (if customer's wallet has
 * funds that can be transferred from an older, inactive keyset).
 */
@JvmInline
value class SweepSyncFrequency(val value: Duration = 5.minutes) {
  @ContributesTo(AppScope::class)
  interface Component {
    @Provides
    fun provideSweepSyncFrequency() = SweepSyncFrequency()
  }
}
