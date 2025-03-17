package build.wallet.money.exchange

import build.wallet.di.AppScope
import me.tatarka.inject.annotations.Provides
import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo
import kotlin.jvm.JvmInline
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Determines how frequently to sync exchange rates.
 */
@JvmInline
value class ExchangeRateSyncFrequency(val value: Duration = 1.minutes) {
  @ContributesTo(AppScope::class)
  interface Component {
    @Provides
    fun provideExchangeRateSyncFrequency() = ExchangeRateSyncFrequency()
  }
}
