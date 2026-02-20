package build.wallet.statemachine.transactions

import app.cash.turbine.test
import build.wallet.bdk.bindings.BdkError
import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitcoin.fees.Fee
import build.wallet.bitcoin.fees.FeeRate
import build.wallet.bitcoin.transactions.BitcoinTransaction.TransactionType.Outgoing
import build.wallet.bitcoin.transactions.BitcoinTransaction.TransactionType.UtxoConsolidation
import build.wallet.bitcoin.transactions.BitcoinWalletServiceFake
import build.wallet.bitcoin.transactions.PsbtMock
import build.wallet.bitcoin.transactions.SpeedUpTransactionDetails
import build.wallet.bitcoin.wallet.SpendingWalletMock
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.coroutines.turbine.awaitUntil
import build.wallet.coroutines.turbine.turbines
import build.wallet.ktor.result.HttpError
import build.wallet.money.BitcoinMoney
import build.wallet.money.exchange.ExchangeRateServiceFake
import build.wallet.nfc.NfcException
import build.wallet.statemachine.BodyStateMachineMock
import build.wallet.statemachine.StateMachineMock
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.send.*
import build.wallet.statemachine.send.signtransaction.SignTransactionNfcSessionUiProps
import build.wallet.statemachine.send.signtransaction.SignTransactionNfcSessionUiStateMachineMock
import build.wallet.statemachine.transactions.fee.FeeEstimationErrorUiStateMachineImpl
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitBodyMock
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.statemachine.utxo.UtxoConsolidationSpeedUpConfirmationModel
import build.wallet.statemachine.utxo.UtxoConsolidationSpeedUpTransactionSentModel
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class FeeBumpConfirmationUiStateMachineImplTests : FunSpec({

  val spendingWallet = SpendingWalletMock(turbines::create)
  val bitcoinWalletService = BitcoinWalletServiceFake()

  val stateMachine = FeeBumpConfirmationUiStateMachineImpl(
    transactionDetailsCardUiStateMachine = object : TransactionDetailsCardUiStateMachine,
      StateMachineMock<TransactionDetailsCardUiProps, TransactionDetailsModel>(
        initialModel =
          TransactionDetailsModel(
            transactionSpeedText = "transactionSpeedText",
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
              )
          )
      ) {},
    exchangeRateService = ExchangeRateServiceFake(),
    signTransactionNfcSessionUiStateMachine = SignTransactionNfcSessionUiStateMachineMock("sign-txn-nfc"),
    transferInitiatedUiStateMachine = object : TransferInitiatedUiStateMachine,
      BodyStateMachineMock<TransferInitiatedUiProps>(
        "transfer-initiated"
      ) {},
    bitcoinWalletService = bitcoinWalletService,
    feeEstimationErrorUiStateMachine = FeeEstimationErrorUiStateMachineImpl()
  )

  val props = FeeBumpConfirmationProps(
    account = FullAccountMock,
    speedUpTransactionDetails = SpeedUpTransactionDetails(
      txid = "1234",
      recipientAddress = BitcoinAddress("1234"),
      sendAmount = BitcoinMoney.sats(10000),
      oldFee = Fee(BitcoinMoney.sats(2000)),
      transactionType = Outgoing
    ),
    onExit = {},
    newFeeRate = FeeRate(2f),
    psbt = PsbtMock
  )

  beforeTest {
    bitcoinWalletService.reset()
    bitcoinWalletService.spendingWallet.value = spendingWallet
  }

  test("fee bump happy path") {
    stateMachine.test(props) {
      awaitBody<TransferConfirmationScreenModel> {
        onConfirmClick()
      }

      awaitBodyMock<SignTransactionNfcSessionUiProps>("sign-txn-nfc") {
        onSuccess(PsbtMock)
      }

      awaitBody<LoadingSuccessBodyModel>()

      bitcoinWalletService.broadcastedPsbts.test {
        awaitUntil(listOf(PsbtMock))
      }

      awaitBodyMock<TransferInitiatedUiProps>("transfer-initiated") {
        onDone()
      }
    }
  }

  test("fee bump for utxo consolidation happy path") {
    stateMachine.test(
      props.copy(
        speedUpTransactionDetails = props.speedUpTransactionDetails.copy(
          transactionType = UtxoConsolidation
        )
      )
    ) {
      awaitBody<UtxoConsolidationSpeedUpConfirmationModel> {
        onConfirmClick()
      }

      awaitBodyMock<SignTransactionNfcSessionUiProps>("sign-txn-nfc") {
        onSuccess(PsbtMock)
      }

      awaitBody<LoadingSuccessBodyModel>()

      bitcoinWalletService.broadcastedPsbts.test {
        awaitUntil(listOf(PsbtMock))
      }

      awaitBody<UtxoConsolidationSpeedUpTransactionSentModel> {
        onDone()
      }
    }
  }

  test("broadcast failure shows insufficient funds error") {
    bitcoinWalletService.broadcastError =
      BdkError.InsufficientFunds(cause = null, message = "insufficient funds")

    stateMachine.test(props) {
      awaitBody<TransferConfirmationScreenModel> { onConfirmClick() }

      awaitBodyMock<SignTransactionNfcSessionUiProps>("sign-txn-nfc") {
        onSuccess(PsbtMock)
      }

      awaitBody<LoadingSuccessBodyModel>()

      awaitUntilBody<FormBodyModel> {
        val headerModel = header.shouldNotBeNull()
        headerModel.headline.shouldBe("We couldn’t speed up this transaction")
        headerModel.sublineModel.shouldNotBeNull().string.shouldBe(
          "There are not enough funds to speed up the transaction. Please add more funds and try again."
        )
      }
    }
  }

  test("network broadcast failure offers retry") {
    bitcoinWalletService.broadcastError =
      HttpError.NetworkError(cause = RuntimeException("offline"))

    stateMachine.test(props) {
      awaitBody<TransferConfirmationScreenModel> { onConfirmClick() }

      awaitBodyMock<SignTransactionNfcSessionUiProps>("sign-txn-nfc") {
        onSuccess(PsbtMock)
      }

      awaitBody<LoadingSuccessBodyModel>()

      awaitUntilBody<FormBodyModel> {
        val headerModel = header.shouldNotBeNull()
        headerModel.headline.shouldBe("We couldn’t determine fees for this transaction")
        val retryButton = primaryButton.shouldNotBeNull()
        retryButton.text.shouldBe("Retry")
        retryButton.onClick()
      }

      awaitBody<LoadingSuccessBodyModel>()

      awaitUntilBody<FormBodyModel> {
        header.shouldNotBeNull().headline.shouldBe("We couldn’t determine fees for this transaction")
      }

      bitcoinWalletService.broadcastedPsbts.value shouldBe listOf(PsbtMock, PsbtMock)
    }
  }

  test("NFC signing error is handled internally by NFC state machine") {
    stateMachine.test(props) {
      awaitBody<TransferConfirmationScreenModel> { onConfirmClick() }

      // The NFC state machine handles errors internally
      // Verify that the onError callback returns false, indicating external handling is not requested
      awaitBodyMock<SignTransactionNfcSessionUiProps>("sign-txn-nfc") {
        val errorHandled = onError(NfcException.CommandError("Failed to sign"))
        errorHandled.shouldBe(false)

        // The NFC state machine would handle the error internally and show its own error UI
        // From the parent state machine's perspective, we remain in the SigningWithHardware state
        // User can exit by tapping back
        onBack()
      }

      // After backing out, we return to the confirmation screen
      awaitBody<TransferConfirmationScreenModel> {
        requiresHardware.shouldBeTrue()
      }
    }
  }

  test("NFC signing back navigation returns to confirmation screen") {
    stateMachine.test(props) {
      awaitBody<TransferConfirmationScreenModel> { onConfirmClick() }

      awaitBodyMock<SignTransactionNfcSessionUiProps>("sign-txn-nfc") {
        psbt.shouldBe(PsbtMock)
        onBack()
      }

      awaitBody<TransferConfirmationScreenModel> {
        requiresHardware.shouldBeTrue()
      }
    }
  }

  test("W3 two-tap flow completes successfully for outgoing transaction") {
    // The W3 two-tap flow is handled internally by SignTransactionNfcSessionUiStateMachine.
    // From this state machine's perspective, it just receives onSuccess with the final signed PSBT.
    stateMachine.test(props) {
      awaitBody<TransferConfirmationScreenModel> {
        onConfirmClick()
      }

      // SignTransactionNfcSessionUiStateMachine handles the W3 flow internally
      // (two NFC taps with hardware confirmation in between) and calls onSuccess
      // when the full flow is complete
      awaitBodyMock<SignTransactionNfcSessionUiProps>("sign-txn-nfc") {
        psbt.shouldBe(PsbtMock)
        onSuccess(PsbtMock)
      }

      awaitBody<LoadingSuccessBodyModel>()

      bitcoinWalletService.broadcastedPsbts.test {
        awaitUntil(listOf(PsbtMock))
      }

      awaitBodyMock<TransferInitiatedUiProps>("transfer-initiated") {
        onDone()
      }
    }
  }

  test("W3 two-tap flow completes successfully for utxo consolidation") {
    stateMachine.test(
      props.copy(
        speedUpTransactionDetails = props.speedUpTransactionDetails.copy(
          transactionType = UtxoConsolidation
        )
      )
    ) {
      awaitBody<UtxoConsolidationSpeedUpConfirmationModel> {
        onConfirmClick()
      }

      // SignTransactionNfcSessionUiStateMachine handles the W3 flow internally
      // and calls onSuccess when complete
      awaitBodyMock<SignTransactionNfcSessionUiProps>("sign-txn-nfc") {
        psbt.shouldBe(PsbtMock)
        onSuccess(PsbtMock)
      }

      awaitBody<LoadingSuccessBodyModel>()

      bitcoinWalletService.broadcastedPsbts.test {
        awaitUntil(listOf(PsbtMock))
      }

      awaitBody<UtxoConsolidationSpeedUpTransactionSentModel> {
        onDone()
      }
    }
  }

  test("W3 flow - NFC error during signing is handled internally") {
    stateMachine.test(props) {
      awaitBody<TransferConfirmationScreenModel> { onConfirmClick() }

      // The W3 NFC signing flow handles errors internally
      // Verify that the onError callback returns false, indicating external handling is not requested
      awaitBodyMock<SignTransactionNfcSessionUiProps>("sign-txn-nfc") {
        val errorHandled = onError(NfcException.CanBeRetried.TagLost())
        errorHandled.shouldBe(false)

        // The NFC state machine would handle the error internally and show its own error UI
        // From the parent state machine's perspective, we remain in the SigningWithHardware state
        // User can exit by tapping back
        onBack()
      }

      // After backing out, we return to the confirmation screen
      awaitBody<TransferConfirmationScreenModel> {
        requiresHardware.shouldBeTrue()
      }
    }
  }
})
