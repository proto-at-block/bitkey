package build.wallet.bitcoin.wallet

import bitkey.account.AccountConfigServiceFake
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.bdk.BdkTransactionMapperV2
import build.wallet.bitcoin.bdk.BdkWalletProviderMock
import build.wallet.bitcoin.descriptor.BitcoinDescriptor.Spending
import build.wallet.bitcoin.fees.BitcoinFeeRateEstimatorMock
import build.wallet.platform.app.AppSessionManagerFake
import build.wallet.testing.shouldBeOk
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import uniffi.bdk.NoPointer
import uniffi.bdk.Persister
import uniffi.bdk.Wallet as BdkV2Wallet

class WalletV2ProviderImplTests : FunSpec({
  val accountConfigService = AccountConfigServiceFake()
  val appSessionManager = AppSessionManagerFake()
  val bitcoinFeeRateEstimator = BitcoinFeeRateEstimatorMock()

  val bdkWalletProvider =
    BdkWalletProviderMock(
      walletV2 = BdkV2Wallet(NoPointer),
      persister = Persister(NoPointer)
    )

  val mapper =
    object : BdkTransactionMapperV2 {
      override suspend fun createTransaction(
        txDetails: uniffi.bdk.TxDetails,
        wallet: BdkV2Wallet,
        networkType: BitcoinNetworkType,
      ) = error("Not used in these tests")

      override fun createUtxo(localOutput: uniffi.bdk.LocalOutput) =
        error("Not used in these tests")
    }

  lateinit var provider: WalletV2ProviderImpl

  beforeTest {
    accountConfigService.reset()
    provider =
      WalletV2ProviderImpl(
        bdkWalletProvider = bdkWalletProvider,
        appSessionManager = appSessionManager,
        accountConfigService = accountConfigService,
        bdkTransactionMapperV2 = mapper,
        bitcoinFeeRateEstimator = bitcoinFeeRateEstimator
      )
  }

  test("creates WalletV2Impl when networks match") {
    accountConfigService.setBitcoinNetworkType(BitcoinNetworkType.SIGNET)

    val descriptor =
      SpendingWalletDescriptor(
        identifier = "test-wallet",
        networkType = BitcoinNetworkType.SIGNET,
        receivingDescriptor = Spending("receiving"),
        changeDescriptor = Spending("change")
      )

    val wallet = provider.getWallet(descriptor).shouldBeOk()
    wallet.identifier.shouldBe(descriptor.identifier)
    wallet.networkType.shouldBe(descriptor.networkType)
    (wallet is WalletV2Impl).shouldBe(true)
  }
})
