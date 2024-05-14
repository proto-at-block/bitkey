package build.wallet.bitcoin.wallet

import app.cash.turbine.test
import build.wallet.analytics.events.AppSessionManagerFake
import build.wallet.bdk.bindings.BdkAddressBuilderMock
import build.wallet.bdk.bindings.BdkPartiallySignedTransactionBuilderMock
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.balance.BitcoinBalance
import build.wallet.bitcoin.bdk.BdkBumpFeeTxBuilderFactoryMock
import build.wallet.bitcoin.bdk.BdkBumpFeeTxBuilderMock
import build.wallet.bitcoin.bdk.BdkTransactionMapperMock
import build.wallet.bitcoin.bdk.BdkTxBuilderFactoryMock
import build.wallet.bitcoin.bdk.BdkTxBuilderMock
import build.wallet.bitcoin.bdk.BdkWalletMock
import build.wallet.bitcoin.bdk.BdkWalletSyncerMock
import build.wallet.coroutines.turbine.turbines
import io.kotest.core.coroutines.backgroundScope
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlin.time.Duration.Companion.seconds

class SpendingWalletImplTests : FunSpec({

  coroutineTestScope = true

  val bdkWallet = BdkWalletMock(turbines::create)
  val bdkWalletSyncer = BdkWalletSyncerMock(turbines::create)
  val bdkAddressBuilder = BdkAddressBuilderMock(turbines::create)
  val appSessionManager = AppSessionManagerFake()

  fun buildWallet(syncScope: CoroutineScope) =
    SpendingWalletImpl(
      identifier = "wallet-identifier",
      bdkWallet = bdkWallet,
      networkType = BitcoinNetworkType.BITCOIN,
      bdkTransactionMapper = BdkTransactionMapperMock(),
      bdkWalletSyncer = bdkWalletSyncer,
      bdkPsbtBuilder = BdkPartiallySignedTransactionBuilderMock(),
      bdkTxBuilderFactory = BdkTxBuilderFactoryMock(BdkTxBuilderMock()),
      bdkAddressBuilder = bdkAddressBuilder,
      bdkBumpFeeTxBuilderFactory = BdkBumpFeeTxBuilderFactoryMock(BdkBumpFeeTxBuilderMock()),
      appSessionManager = appSessionManager,
      syncContext = syncScope.coroutineContext
    )

  beforeEach {
    appSessionManager.reset()
  }

  test("Balance initialization") {
    val wallet = buildWallet(backgroundScope)
    wallet.balance().test {
      wallet.initializeBalanceAndTransactions()
      awaitItem().shouldBe(BitcoinBalance.ZeroBalance)
    }
  }

  test("Transactions initialization") {
    val wallet = buildWallet(backgroundScope)
    wallet.transactions().test {
      wallet.initializeBalanceAndTransactions()
      awaitItem().shouldBeEmpty()
    }
  }

  test("syncs do not occur while app is backgrounded") {
    val wallet = buildWallet(backgroundScope)
    appSessionManager.appDidEnterBackground()
    wallet.launchPeriodicSync(scope = backgroundScope, interval = 3.seconds)
    bdkWalletSyncer.syncCalls.expectNoEvents()

    appSessionManager.appDidEnterForeground()

    bdkWalletSyncer.syncCalls.awaitItem()
  }
})
