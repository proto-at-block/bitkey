package build.wallet.statemachine.send

import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority.SIXTY_MINUTES
import build.wallet.money.BitcoinMoney
import build.wallet.statemachine.StateMachineMock
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.testWithVirtualTime
import build.wallet.statemachine.transactions.TransactionDetails
import build.wallet.statemachine.ui.awaitBody
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
                transferAmountSecondaryText = "transferAmountBtcText",
                feeAmountText = "feeFiatAmountText",
                feeAmountSecondaryText = "feeAmountBtcText",
                totalAmountPrimaryText = "totalFiatAmountText",
                totalAmountSecondaryText = "totalBitcoinAmountText"
              )
          )
      ) {}

  val stateMachine = TransferInitiatedUiStateMachineImpl(
    transactionDetailsCardUiStateMachine = transactionDetailsCardUiStateMachine
  )

  val regularProps =
    TransferInitiatedUiProps(
      onBack = {},
      recipientAddress = BitcoinAddress("abc"),
      transactionDetails =
        TransactionDetails.Regular(
          transferAmount = BitcoinMoney.sats(3861),
          feeAmount = BitcoinMoney.sats(380),
          estimatedTransactionPriority = SIXTY_MINUTES
        ),
      exchangeRates = null,
      onDone = {}
    )

  val speedUpProps =
    regularProps.copy(
      transactionDetails =
        TransactionDetails.SpeedUp(
          transferAmount = BitcoinMoney.sats(3861),
          oldFeeAmount = BitcoinMoney.sats(190),
          feeAmount = BitcoinMoney.sats(380)
        )
    )

  test("show regular transaction details: transfer amount, fees paid") {
    stateMachine.testWithVirtualTime(regularProps) {
      awaitBody<FormBodyModel> {
        mainContentList[0].shouldBeTypeOf<FormMainContentModel.Divider>()
        with(mainContentList[2].shouldBeTypeOf<FormMainContentModel.DataList>()) {
          items.size.shouldBe(2)

          items[0].title.shouldBe("Amount")
          items[1].title.shouldBe("Network fees")
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
            transferAmountSecondaryText = "transferAmountBtcText",
            oldFeeAmountText = "oldFeeAmountText",
            oldFeeAmountSecondaryText = "oldFeeAmountBtcText",
            feeDifferenceText = "feeDifferenceText",
            feeDifferenceSecondaryText = "feeDifferenceBtcText",
            totalAmountPrimaryText = "totalFiatAmountText",
            totalAmountSecondaryText = "totalBitcoinAmountText",
            totalFeeText = "totalFeeText",
            totalFeeSecondaryText = "totalFeeBtcText"
          ),
        transactionSpeedText = "transactionSpeedText"
      )
    )

    stateMachine.testWithVirtualTime(speedUpProps) {
      awaitBody<FormBodyModel> {
        mainContentList[0].shouldBeTypeOf<FormMainContentModel.Divider>()
        with(mainContentList[2].shouldBeTypeOf<FormMainContentModel.DataList>()) {
          items.size.shouldBe(3)

          items[0].title.shouldBe("Amount")
          items[1].title.shouldBe("Original network fee")
          items[2].title.shouldBe("Speed up network fee")
        }
      }
    }
  }
})
