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
        exchangeRates = immutableListOf(),
        variant = TransferConfirmationScreenVariant.Regular
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
        exchangeRates = immutableListOf(),
        variant = TransferConfirmationScreenVariant.SpeedUp
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

  context("Show sell transaction detail card") {
    val sellTransactionDetails = TransactionDetails.Sell(
      transferAmount = BitcoinMoney.btc(2.0),
      feeAmount = BitcoinMoney.btc(0.1),
      estimatedTransactionPriority = EstimatedTransactionPriority.FASTEST
    )

    val props = TransactionDetailsCardUiProps(
      transactionDetails = sellTransactionDetails,
      exchangeRates = immutableListOf(),
      variant = TransferConfirmationScreenVariant.Sell("partnerName")
    )

    test("Generates correct detail model type with correct transfer amounts") {
      stateMachine.test(props) {
        val transactionDetails = awaitItem().transactionDetailModelType
          .shouldBeTypeOf<TransactionDetailModelType.Sell>()

        transactionDetails.transferAmountText.shouldBe("~$6.00")
        transactionDetails.transferAmountSecondaryText.shouldBe("200,000,000 sats")
        transactionDetails.totalAmountPrimaryText.shouldBe("$6.30")
        transactionDetails.totalAmountSecondaryText.shouldBe("210,000,000 sats")
        transactionDetails.feeAmountText.shouldBe("$0.30")
      }
    }

    test("Shows correct arrival estimate") {
      stateMachine.test(props) {
        awaitItem().transactionSpeedText.shouldBe("~10 minutes")
      }

      val thirtyMinutesSellDetail = sellTransactionDetails.copy(
        estimatedTransactionPriority = EstimatedTransactionPriority.THIRTY_MINUTES
      )
      val thirtyMinutesProps = props.copy(transactionDetails = thirtyMinutesSellDetail)
      stateMachine.test(thirtyMinutesProps) {
        awaitItem().transactionSpeedText.shouldBe("~30 minutes")
      }

      val sixtyMinutesSellDetail = sellTransactionDetails.copy(
        estimatedTransactionPriority = EstimatedTransactionPriority.SIXTY_MINUTES
      )
      val sixtyMinutesProps = props.copy(transactionDetails = sixtyMinutesSellDetail)
      stateMachine.test(sixtyMinutesProps) {
        awaitItem().transactionSpeedText.shouldBe("~60 minutes")
      }
    }

    test("Handles zero fee correctly") {
      val zeroFeeSellDetail = sellTransactionDetails.copy(
        feeAmount = BitcoinMoney.btc(0.0)
      )
      val zeroFeeProps = props.copy(transactionDetails = zeroFeeSellDetail)
      stateMachine.test(zeroFeeProps) {
        val transactionDetails = awaitItem().transactionDetailModelType
          .shouldBeTypeOf<TransactionDetailModelType.Sell>()

        transactionDetails.feeAmountText.shouldBe("$0.00")
        transactionDetails.totalAmountPrimaryText.shouldBe("$6.00")
        transactionDetails.totalAmountSecondaryText.shouldBe("200,000,000 sats")
      }
    }

    test("Handles null exchange rates by showing BTC as primary") {
      val propsWithNullRates = props.copy(exchangeRates = null)
      stateMachine.test(propsWithNullRates) {
        val transactionDetails = awaitItem().transactionDetailModelType
          .shouldBeTypeOf<TransactionDetailModelType.Sell>()

        transactionDetails.transferAmountText.shouldBe("~200,000,000 sats")
        transactionDetails.transferAmountSecondaryText.shouldBe("")
        transactionDetails.totalAmountPrimaryText.shouldBe("210,000,000 sats")
        transactionDetails.totalAmountSecondaryText.shouldBe("")
        transactionDetails.feeAmountText.shouldBe("10,000,000 sats")
      }
    }
  }
})
