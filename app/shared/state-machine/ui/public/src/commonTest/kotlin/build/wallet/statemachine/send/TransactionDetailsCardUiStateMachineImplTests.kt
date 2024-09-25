package build.wallet.statemachine.send

import build.wallet.bitcoin.transactions.EstimatedTransactionPriority
import build.wallet.bitcoin.transactions.TransactionDetails
import build.wallet.compose.collections.immutableListOf
import build.wallet.money.BitcoinMoney
import build.wallet.money.display.FiatCurrencyPreferenceRepositoryFake
import build.wallet.money.exchange.CurrencyConverterFake
import build.wallet.money.formatter.MoneyDisplayFormatterFake
import build.wallet.statemachine.core.test
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf

class TransactionDetailsCardUiStateMachineImplTests : FunSpec({
  val stateMachine =
    TransactionDetailsCardUiStateMachineImpl(
      currencyConverter = CurrencyConverterFake(),
      fiatCurrencyPreferenceRepository = FiatCurrencyPreferenceRepositoryFake(),
      moneyDisplayFormatter = MoneyDisplayFormatterFake
    )

  context("Show regular transaction detail card") {
    val props =
      TransactionDetailsCardUiProps(
        transactionDetails =
          TransactionDetails.Regular(
            transferAmount = BitcoinMoney.btc(1.0),
            feeAmount = BitcoinMoney.btc(0.3),
            estimatedTransactionPriority = EstimatedTransactionPriority.FASTEST
          ),
        exchangeRates = immutableListOf()
      )

    test("Generates correct detail model type") {
      stateMachine.test(props) {
        val transactionDetails =
          awaitItem().transactionDetailModelType.shouldBeTypeOf<TransactionDetailModelType.Regular>()

        transactionDetails.transferAmountText.shouldBe("$3.00")
        transactionDetails.feeAmountText.shouldBe("$0.90")
      }
    }

    test("Shows correct arrival estimate") {
      val transactionDetails = props.transactionDetails.shouldBeTypeOf<TransactionDetails.Regular>()
      stateMachine.test(props) {
        awaitItem().transactionSpeedText.shouldBe("~10 minutes")
      }
      val thirtyMinutesTransactionDetail =
        transactionDetails.copy(
          estimatedTransactionPriority = EstimatedTransactionPriority.THIRTY_MINUTES
        )
      stateMachine.test(props.copy(transactionDetails = thirtyMinutesTransactionDetail)) {
        awaitItem().transactionSpeedText.shouldBe("~30 minutes")
      }
      val sixtyMinutesTransactionDetail =
        transactionDetails.copy(
          estimatedTransactionPriority = EstimatedTransactionPriority.SIXTY_MINUTES
        )
      stateMachine.test(props.copy(transactionDetails = sixtyMinutesTransactionDetail)) {
        awaitItem().transactionSpeedText.shouldBe("~60 minutes")
      }
    }
  }

  context("Show speed up transaction detail card") {
    val props =
      TransactionDetailsCardUiProps(
        transactionDetails =
          TransactionDetails.SpeedUp(
            transferAmount = BitcoinMoney.btc(5.0),
            feeAmount = BitcoinMoney.btc(0.6),
            oldFeeAmount = BitcoinMoney.btc(0.3)
          ),
        exchangeRates = immutableListOf()
      )

    test("Generates correct detail model type with correct transfer amounts") {
      stateMachine.test(props) {
        val transactionDetails =
          awaitItem().transactionDetailModelType.shouldBeTypeOf<TransactionDetailModelType.SpeedUp>()

        transactionDetails.transferAmountText.shouldBe("$15.00")
        transactionDetails.oldFeeAmountText.shouldBe("$0.90")
        transactionDetails.feeDifferenceText.shouldBe("+$0.90")
      }
    }

    test("Shows fastest arrival estimate") {
      stateMachine.test(props) {
        awaitItem().transactionSpeedText.shouldBe("~10 minutes")
      }
    }
  }
})
