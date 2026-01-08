package build.wallet.bitcoin.fees

import bitkey.datadog.DatadogRumMonitorFake
import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount
import build.wallet.bitcoin.transactions.BitcoinWalletServiceFake
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority.*
import build.wallet.bitcoin.transactions.PsbtMock
import build.wallet.bitcoin.wallet.SpendingWalletMock
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.ktor.result.HttpError
import build.wallet.money.BitcoinMoney
import build.wallet.testing.shouldBeErrOfType
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.unwrap
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe

class BitcoinTransactionFeeEstimatorImplTests : FunSpec({
  val bitcoinFeeRateEstimator = BitcoinFeeRateEstimatorMock()
  val spendingWallet = SpendingWalletMock(turbines::create)
  val bitcoinWalletService = BitcoinWalletServiceFake()
  val estimator =
    BitcoinTransactionFeeEstimatorImpl(
      bitcoinFeeRateEstimator = bitcoinFeeRateEstimator,
      bitcoinWalletService = bitcoinWalletService,
      datadogRumMonitor = DatadogRumMonitorFake(turbines::create)
    )

  beforeTest {
    spendingWallet.reset()
    bitcoinWalletService.reset()
    bitcoinWalletService.spendingWallet.value = spendingWallet
  }

  test("estimator returns a filled map of fees") {
    spendingWallet.createPsbtResult =
      Ok(
        PsbtMock.copy(
          fee = Fee(BitcoinMoney.sats(200)),
          baseSize = 2000,
          numOfInputs = 1,
          amountSats = 100UL
        )
      )

    val fees =
      estimator.getFeesForTransaction(
        priorities = EstimatedTransactionPriority.entries,
        account = FullAccountMock,
        recipientAddress = BitcoinAddress(address = ""),
        amount = BitcoinTransactionSendAmount.ExactAmount(BitcoinMoney.zero())
      ).unwrap()

    // *Note* size is hardcoded to be 2000
    // Fee rates are fastest -> 3, thirty mins -> 2, sixty mins -> 1
    // We assume a total size of 2000 + 253 for a weight of (3 * 2000) + 2253 = 8253
    // This gives us a vsize of 2064, and each fee is this value times the fee rate

    fees.size.shouldBe(3)
    fees[FASTEST].shouldBe(Fee(amount = BitcoinMoney.sats(6192)))
    fees[THIRTY_MINUTES].shouldBe(Fee(amount = BitcoinMoney.sats(4128)))
    fees[SIXTY_MINUTES].shouldBe(Fee(amount = BitcoinMoney.sats(2064)))
  }

  test("estimator propagates fee rate estimator errors") {
    bitcoinFeeRateEstimator.getEstimatedFeeRateResult = Err(HttpError.NetworkError(Throwable()))
    val result =
      estimator.getFeesForTransaction(
        priorities = EstimatedTransactionPriority.entries,
        account = FullAccountMock,
        recipientAddress = BitcoinAddress(address = ""),
        amount = BitcoinTransactionSendAmount.ExactAmount(BitcoinMoney.zero())
      )
    result.shouldBeErrOfType<BitcoinTransactionFeeEstimator.FeeEstimationError.CannotGetFeesError>()
      .isConnectivityError.shouldBeTrue()
  }
})
