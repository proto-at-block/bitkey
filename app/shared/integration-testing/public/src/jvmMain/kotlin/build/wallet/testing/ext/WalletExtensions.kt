@file:OptIn(FlowPreview::class)

package build.wallet.testing.ext

import build.wallet.bitcoin.balance.BitcoinBalance
import build.wallet.bitcoin.wallet.SpendingWallet
import build.wallet.money.BitcoinMoney
import build.wallet.testing.AppTester
import build.wallet.testing.shouldBeOk
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.assertions.nondeterministic.eventuallyConfig
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.timeout
import kotlin.time.Duration.Companion.seconds

/**
 * Syncs wallet of active Full account until it has balance greater than [minimumBalance],
 * waiting for non-zero balance by default.
 */
suspend fun AppTester.waitForFunds(
  balancePredicate: (BitcoinBalance) -> Boolean = { it.total.isPositive },
): BitcoinBalance {
  lateinit var balance: BitcoinBalance
  eventually(
    eventuallyConfig {
      duration = 60.seconds
      interval = 1.seconds
      initialDelay = 1.seconds
    }
  ) {
    val activeWallet = getActiveWallet()
    activeWallet.sync().shouldBeOk()
    balance = activeWallet.balance().first()
    balancePredicate(balance).shouldBeTrue()
    // Eventually could iterate to calculate and subtract psbtsGeneratedData.totalFeeAmount)
  }
  return balance
}

/**
 * Returns the active spending wallet.
 */
suspend fun AppTester.getActiveWallet(): SpendingWallet {
  getActiveAccount()
  return app.appComponent.transactionsService.spendingWallet()
    .filterNotNull()
    .timeout(2.seconds)
    .first()
}

/**
 * Mines a new block if on Regtest. Noop otherwise. Call this after sending transactions
 * to make tests runnable on Regtest and other networks. Mining rewards are sent to the Treasury.
 */
suspend fun AppTester.mineBlock() {
  blockchainControl.mineBlocks(1)
}

/**
 * Validates that current wallet has total balance equal to [amount].
 */
suspend fun AppTester.shouldHaveTotalBalance(amount: BitcoinMoney) {
  getActiveWallet().balance().first().total.shouldBe(amount)
}
