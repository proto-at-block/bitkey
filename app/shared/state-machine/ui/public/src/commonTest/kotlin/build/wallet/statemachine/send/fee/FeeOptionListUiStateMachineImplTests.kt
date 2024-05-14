package build.wallet.statemachine.send.fee

import build.wallet.bitcoin.fees.Fee
import build.wallet.bitcoin.fees.oneSatPerVbyteFeeRate
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority.FASTEST
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority.SIXTY_MINUTES
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority.THIRTY_MINUTES
import build.wallet.compose.collections.immutableListOf
import build.wallet.coroutines.turbine.turbines
import build.wallet.money.BitcoinMoney
import build.wallet.money.display.FiatCurrencyPreferenceRepositoryMock
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.keybox.ActiveKeyboxLoadedDataMock
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import kotlinx.collections.immutable.persistentMapOf

class FeeOptionListUiStateMachineImplTests : FunSpec({
  val feeOptionUiStateMachine = FeeOptionUiStateMachineMock()
  val fiatCurrencyPreferenceRepository = FiatCurrencyPreferenceRepositoryMock(turbines::create)

  val stateMachine = FeeOptionListUiStateMachineImpl(
    feeOptionUiStateMachine = feeOptionUiStateMachine,
    fiatCurrencyPreferenceRepository = fiatCurrencyPreferenceRepository
  )

  val props = FeeOptionListProps(
    accountData = ActiveKeyboxLoadedDataMock,
    transactionBaseAmount = BitcoinMoney.btc(1.0),
    fees = persistentMapOf(
      FASTEST to Fee(BitcoinMoney.btc(10.0), oneSatPerVbyteFeeRate),
      THIRTY_MINUTES to Fee(BitcoinMoney.btc(2.0), oneSatPerVbyteFeeRate),
      SIXTY_MINUTES to Fee(BitcoinMoney.btc(1.0), oneSatPerVbyteFeeRate)
    ),
    defaultPriority = THIRTY_MINUTES,
    exchangeRates = immutableListOf(),
    onOptionSelected = {}
  )

  test("list is created with all fees") {
    stateMachine.test(props = props) {
      awaitItem().apply {
        options.size.shouldBe(3)
        options[1].selected.shouldBeTrue()
      }
    }
  }
})
