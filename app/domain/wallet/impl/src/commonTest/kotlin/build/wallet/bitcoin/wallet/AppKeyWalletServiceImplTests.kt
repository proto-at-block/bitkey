package build.wallet.bitcoin.wallet

import build.wallet.bitcoin.address.BitcoinAddressServiceFake
import build.wallet.bitcoin.fees.BitcoinFeeRateEstimatorMock
import build.wallet.bitcoin.transactions.BitcoinWalletServiceFake
import build.wallet.bitkey.spending.SpendingKeysetMock
import build.wallet.testing.shouldBeErrOfType
import com.github.michaelbull.result.Err
import io.kotest.core.spec.style.FunSpec

class AppKeyWalletServiceImplTests : FunSpec({
  val appKeyWalletProvider = AppKeyWalletProviderFake()
  val service = AppKeyWalletServiceImpl(
    appKeyWalletProvider = appKeyWalletProvider,
    bitcoinAddressService = BitcoinAddressServiceFake(),
    bitcoinWalletService = BitcoinWalletServiceFake(),
    bitcoinFeeRateEstimator = BitcoinFeeRateEstimatorMock()
  )

  beforeTest {
    appKeyWalletProvider.reset()
  }

  test("preserves private key unavailable error") {
    appKeyWalletProvider.walletResult = Err(
      AppKeyWalletError.PrivateKeyUnavailable(SpendingKeysetMock.localId)
    )

    service.fullScan(SpendingKeysetMock)
      .shouldBeErrOfType<AppKeyWalletServiceError.PrivateKeyUnavailable>()
  }
})
