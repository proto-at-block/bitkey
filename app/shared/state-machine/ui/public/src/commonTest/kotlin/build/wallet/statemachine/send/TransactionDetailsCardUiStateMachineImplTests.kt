package build.wallet.statemachine.send

import build.wallet.bitcoin.transactions.EstimatedTransactionPriority
import build.wallet.compose.collections.immutableListOf
import build.wallet.money.BitcoinMoney
import build.wallet.money.currency.USD
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
      moneyDisplayFormatter = MoneyDisplayFormatterFake
    )

  context("Show regular transaction detail card") {
    val props =
      TransactionDetailsCardUiProps(
        transactionDetail =
          TransactionDetailType.Regular(
            transferBitcoinAmount = BitcoinMoney.btc(1.0),
            feeBitcoinAmount = BitcoinMoney.btc(0.3),
            estimatedTransactionPriority = EstimatedTransactionPriority.FASTEST
          ),
        fiatCurrency = USD,
        exchangeRates = immutableListOf()
      )

    test("Generates correct detail model type") {
      stateMachine.test(props) {
        val transactionDetail =
          awaitItem().transactionDetailModelType.shouldBeTypeOf<TransactionDetailModelType.Regular>()

        transactionDetail.transferAmountText.shouldBe("$3.00")
        transactionDetail.feeAmountText.shouldBe("$0.90")
      }
    }

    test("Shows correct arrival estimate") {
      val transactionDetail = props.transactionDetail.shouldBeTypeOf<TransactionDetailType.Regular>()
      stateMachine.test(props) {
        awaitItem().transactionSpeedText.shouldBe("~10 minutes")
      }
      val thirtyMinutesTransactionDetail =
        transactionDetail.copy(
          estimatedTransactionPriority = EstimatedTransactionPriority.THIRTY_MINUTES
        )
      stateMachine.test(props.copy(transactionDetail = thirtyMinutesTransactionDetail)) {
        awaitItem().transactionSpeedText.shouldBe("~30 minutes")
      }
      val sixtyMinutesTransactionDetail =
        transactionDetail.copy(
          estimatedTransactionPriority = EstimatedTransactionPriority.SIXTY_MINUTES
        )
      stateMachine.test(props.copy(transactionDetail = sixtyMinutesTransactionDetail)) {
        awaitItem().transactionSpeedText.shouldBe("~60 minutes")
      }
    }
  }

  context("Show speed up transaction detail card") {
    val props =
      TransactionDetailsCardUiProps(
        transactionDetail =
          TransactionDetailType.SpeedUp(
            transferBitcoinAmount = BitcoinMoney.btc(5.0),
            feeBitcoinAmount = BitcoinMoney.btc(0.6),
            oldFeeBitcoinAmount = BitcoinMoney.btc(0.3)
          ),
        fiatCurrency = USD,
        exchangeRates = immutableListOf()
      )

    test("Generates correct detail model type with correct transfer amounts") {
      stateMachine.test(props) {
        val transactionDetail =
          awaitItem().transactionDetailModelType.shouldBeTypeOf<TransactionDetailModelType.SpeedUp>()

        transactionDetail.transferAmountText.shouldBe("$15.00")
        transactionDetail.oldFeeAmountText.shouldBe("$0.90")
        transactionDetail.feeDifferenceText.shouldBe("+$0.90")
      }
    }

    test("Shows fastest arrival estimate") {
      stateMachine.test(props) {
        awaitItem().transactionSpeedText.shouldBe("~10 minutes")
      }
    }
  }
})
