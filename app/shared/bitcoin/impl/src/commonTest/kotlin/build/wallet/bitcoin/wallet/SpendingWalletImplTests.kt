package build.wallet.bitcoin.wallet

import app.cash.turbine.test
import build.wallet.LoadableValue
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
import build.wallet.bitcoin.transactions.BitcoinTransaction
import build.wallet.coroutines.turbine.turbines
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.types.shouldBeTypeOf

class SpendingWalletImplTests : FunSpec({

  val bdkWallet = BdkWalletMock(turbines::create)
  val bdkWalletSyncer = BdkWalletSyncerMock(turbines::create)
  val bdkAddressBuilder = BdkAddressBuilderMock(turbines::create)

  fun buildWallet() =
    SpendingWalletImpl(
      identifier = "wallet-identifier",
      bdkWallet = bdkWallet,
      networkType = BitcoinNetworkType.BITCOIN,
      bdkTransactionMapper = BdkTransactionMapperMock(),
      bdkWalletSyncer = bdkWalletSyncer,
      bdkPsbtBuilder = BdkPartiallySignedTransactionBuilderMock(),
      bdkTxBuilderFactory = BdkTxBuilderFactoryMock(BdkTxBuilderMock()),
      bdkAddressBuilder = bdkAddressBuilder,
      bdkBumpFeeTxBuilderFactory = BdkBumpFeeTxBuilderFactoryMock(BdkBumpFeeTxBuilderMock())
    )

  test("Balance initialization") {
    val wallet = buildWallet()
    wallet.balance().test {
      awaitItem().shouldBeTypeOf<LoadableValue.InitialLoading>()
      wallet.initializeBalanceAndTransactions()
      awaitItem().shouldBeTypeOf<LoadableValue.LoadedValue<BitcoinBalance>>()
    }
  }

  test("Transactions initialization") {
    val wallet = buildWallet()
    wallet.transactions().test {
      awaitItem().shouldBeTypeOf<LoadableValue.InitialLoading>()
      wallet.initializeBalanceAndTransactions()
      awaitItem().shouldBeTypeOf<LoadableValue.LoadedValue<List<BitcoinTransaction>>>()
    }
  }
})
