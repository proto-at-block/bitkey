package build.wallet.statemachine.send

import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority
import build.wallet.money.BitcoinMoney
import build.wallet.money.currency.USD
import build.wallet.statemachine.StateMachineMock
import build.wallet.statemachine.core.awaitBody
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.test
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf

class TransferInitiatedUiStateMachineImplTests : FunSpec({
  val transactionDetailsCardUiStateMachine =
    object : TransactionDetailsCardUiStateMachine,
      StateMachineMock<TransactionDetailsCardUiProps, TransactionDetailsModel>(
        initialModel =
          TransactionDetailsModel(
            transactionSpeedText = "transactionSpeedText",
            transactionDetailModelType =
              TransactionDetailModelType.Regular(
                transferAmountText = "transferFiatAmountText",
                feeAmountText = "feeFiatAmountText",
                totalAmountPrimaryText = "totalFiatAmountText",
                totalAmountSecondaryText = "totalBitcoinAmountText"
              )
          )
      ) {}

  val stateMachine =
    TransferInitiatedUiStateMachineImpl(
      transactionDetailsCardUiStateMachine = transactionDetailsCardUiStateMachine
    )

  val regularProps =
    TransferInitiatedUiProps(
      onBack = {},
      recipientAddress = BitcoinAddress("abc"),
      transferInitiatedVariant =
        TransferInitiatedUiProps.Variant.Regular(
          transferBitcoinAmount = BitcoinMoney.sats(3861),
          feeBitcoinAmount = BitcoinMoney.sats(380),
          totalBitcoinAmount = BitcoinMoney.sats(4241)
        ),
      estimatedTransactionPriority = EstimatedTransactionPriority.FASTEST,
      fiatCurrency = USD,
      exchangeRates = null,
      onDone = {}
    )

  val speedUpProps =
    regularProps.copy(
      transferInitiatedVariant =
        TransferInitiatedUiProps.Variant.SpeedUp(
          transferBitcoinAmount = BitcoinMoney.sats(3861),
          oldFeeAmount = BitcoinMoney.sats(190),
          newFeeAmount = BitcoinMoney.sats(380),
          totalBitcoinAmount = BitcoinMoney.sats(4241)
        )
    )

  test("show regular transaction details: transfer amount, fees paid") {
    stateMachine.test(regularProps) {
      awaitBody<FormBodyModel> {
        with(mainContentList[1].shouldBeTypeOf<FormMainContentModel.DataList>()) {
          items.size.shouldBe(2)

          items[0].title.shouldBe("Recipient receives")
          items[1].title.shouldBe("Network Fees")
        }
      }
    }
  }

  test("show speed up details: transfer amount, old fee amount, and new fee amount") {
    transactionDetailsCardUiStateMachine.emitModel(
      TransactionDetailsModel(
        transactionDetailModelType =
          TransactionDetailModelType.SpeedUp(
            transferAmountText = "transferAmountText",
            oldFeeAmountText = "oldFeeAmountText",
            feeDifferenceText = "feeDifferenceText",
            totalAmountPrimaryText = "totalAmountPrimaryText",
            totalAmountSecondaryText = "totalAmountSecondaryText"
          ),
        transactionSpeedText = "transactionSpeedText"
      )
    )

    stateMachine.test(speedUpProps) {
      awaitBody<FormBodyModel> {
        with(mainContentList[1].shouldBeTypeOf<FormMainContentModel.DataList>()) {
          items.size.shouldBe(3)

          items[0].title.shouldBe("Recipient receives")
          items[1].title.shouldBe("Original network fee")
          items[2].title.shouldBe("Speed up network fee")
        }
      }
    }
  }
})
