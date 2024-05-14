package build.wallet.testing.ext

import build.wallet.bitcoin.wallet.SpendingWallet
import build.wallet.money.BitcoinMoney
import build.wallet.money.matchers.shouldBeGreaterThan
import build.wallet.testing.AppTester
import build.wallet.testing.shouldBeOk
import com.github.michaelbull.result.getOrThrow
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.assertions.nondeterministic.eventuallyConfig
import kotlinx.coroutines.flow.first
import kotlin.time.Duration.Companion.seconds

/**
 * Syncs wallet of active Full account until it has some funds - balance is not zero.
 */
suspend fun AppTester.waitForFunds() {
  val activeAccount = getActiveFullAccount()
  val activeWallet = app.appComponent.appSpendingWalletProvider
    .getSpendingWallet(activeAccount)
    .getOrThrow()
  eventually(
    eventuallyConfig {
      duration = 60.seconds
      interval = 1.seconds
      initialDelay = 1.seconds
    }
  ) {
    activeWallet.sync().shouldBeOk()
    val balance = activeWallet.balance().first()
    balance.total.shouldBeGreaterThan(BitcoinMoney.sats(0))
    // Eventually could iterate to calculate and subtract psbtsGeneratedData.totalFeeAmount)
  }
}

/**
 * Returns the active
 */
suspend fun AppTester.getActiveWallet(): SpendingWallet {
  return app.appComponent.appSpendingWalletProvider
    .getSpendingWallet(getActiveFullAccount())
    .getOrThrow()
}

/**
 * Mines a new block if on Regtest. Noop otherwise. Call this after sending transactions
 * to make tests runnable on Regtest and other networks. Mining rewards are sent to the Treasury.
 */
suspend fun AppTester.mineBlock() {
  blockchainControl.mineBlocks(1)
}
