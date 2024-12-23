@file:OptIn(FlowPreview::class)

package build.wallet.testing.ext

import build.wallet.bitcoin.balance.BitcoinBalance
import build.wallet.bitcoin.wallet.SpendingWallet
import build.wallet.money.BitcoinMoney
import build.wallet.testing.AppTester
import build.wallet.testing.shouldBeOk
import build.wallet.withRealTimeout
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.assertions.nondeterministic.eventuallyConfig
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlin.time.Duration.Companion.seconds

/**
 * Syncs wallet of active Full account until it has balance greater than [minimumBalance],
 * waiting for non-zero balance by default.
 */
suspend fun AppTester.waitForFunds(
  wallet: SpendingWallet? = null,
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
    val activeWallet = wallet ?: getActiveWallet()
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
  return withRealTimeout(2.seconds) {
    bitcoinWalletService.spendingWallet()
      .filterNotNull()
      .first()
  }
}

/**
 * Mines a new block if on Regtest. Noop otherwise. Call this after sending transactions
 * to make tests runnable on Regtest and other networks. Mining rewards are sent to the Treasury.
 */
suspend fun AppTester.mineBlock() {
  blockchainControl.mineBlocks(1)
}

/**
 * Mines a new block if on Regtest and a txid is observed in the mempool. Noop otherwise. Call this
 * after sending transactions to make tests runnable on Regtest and other networks. Mining rewards
 * are sent to the Treasury.
 */
suspend fun AppTester.mineBlock(txid: String) {
  blockchainControl.mineBlock(txid)
}

/**
 * Validates that current wallet has total balance equal to [amount].
 */
suspend fun AppTester.shouldHaveTotalBalance(amount: BitcoinMoney) {
  getActiveWallet().balance().first().total.shouldBe(amount)
}
