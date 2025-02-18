package build.wallet.bitcoin.transactions

import build.wallet.di.AppScope
import me.tatarka.inject.annotations.Provides
import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo
import kotlin.jvm.JvmInline
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Defines the frequency at which we sync bitcoin wallet.
 */
@JvmInline
value class BitcoinWalletSyncFrequency(val value: Duration = 10.seconds) {
  @ContributesTo(AppScope::class)
  interface Component {
    @Provides
    fun provideBitcoinWalletSyncFrequency() = BitcoinWalletSyncFrequency()
  }
}
