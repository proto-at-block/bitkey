package build.wallet.statemachine.send

import app.cash.turbine.Turbine
import app.cash.turbine.test
import build.wallet.bdk.bindings.BdkError
import build.wallet.bitcoin.transactions.BitcoinWalletServiceFake
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority.FASTEST
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitcoin.transactions.TransactionPriorityPreferenceFake
import build.wallet.bitcoin.wallet.SpendingWalletMock
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.ktor.result.HttpError.NetworkError
import build.wallet.limit.DailySpendingLimitStatus
import build.wallet.limit.MobilePayEnabledDataMock
import build.wallet.limit.MobilePayServiceMock
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.testWithVirtualTime
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitBodyMock
import build.wallet.statemachine.ui.clickPrimaryButton
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

fun FunSpec.transferConfirmationUiStateMachineTests(
  props: TransferConfirmationUiProps,
  onTransferInitiatedCalls: Turbine<Psbt>,
  onBackCalls: Turbine<Unit>,
  onExitCalls: Turbine<Unit>,
  spendingWallet: SpendingWalletMock,
  bitcoinWalletService: BitcoinWalletServiceFake,
  transactionPriorityPreference: TransactionPriorityPreferenceFake,
  mobilePayService: MobilePayServiceMock,
  appSignedPsbt: Psbt,
  appAndHwSignedPsbt: Psbt,
  stateMachine: TransferConfirmationUiStateMachineImpl,
  nfcSessionUIStateMachineId: String,
) {
  test("onBack from TransferConfirmationScreenModel invokes props.onBack") {
    stateMachine.testWithVirtualTime(props) {
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      mobilePayService.getDailySpendingLimitStatusCalls.awaitItem()

      awaitBody<TransferConfirmationScreenModel> {
        onBack()
      }

      onBackCalls.awaitItem()
    }
  }

  test("create unsigned psbt error - insufficient funds") {
    spendingWallet.createSignedPsbtResult =
      Err(BdkError.InsufficientFunds(Exception(""), null))

    stateMachine.testWithVirtualTime(props) {
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      // Error screen
      awaitBody<FormBodyModel> {
        with(header.shouldNotBeNull()) {
          headline.shouldBe("We couldn’t send this transaction")
          sublineModel.shouldNotBeNull().string.shouldBe(
            "The amount you are trying to send is too high. Please decrease the amount and try again."
          )
        }
        with(primaryButton.shouldNotBeNull()) {
          text.shouldBe("Go Back")
          onClick()
        }
      }
      onBackCalls.awaitItem()
    }
  }

  test("create unsigned psbt error - other error") {
    spendingWallet.createSignedPsbtResult = Err(BdkError.Generic(Exception(""), null))

    stateMachine.testWithVirtualTime(props) {
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitBody<FormBodyModel> {
        expectGenericErrorMessage()
        clickPrimaryButton()
      }
      onExitCalls.awaitItem()
    }
  }

  test("[app & hw] failure to sign with app key presents error message") {
    spendingWallet.createSignedPsbtResult = Err(BdkError.Generic(Exception(""), null))
    transactionPriorityPreference.preference.shouldBeNull()

    stateMachine.testWithVirtualTime(props) {
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitBody<FormBodyModel> {
        expectGenericErrorMessage()
        clickPrimaryButton()
      }
      onExitCalls.awaitItem()
    }

    transactionPriorityPreference.preference.shouldBeNull()
  }

  test("[app & hw] successfully signing, but failing to broadcast presents error") {
    val transactionPriority = FASTEST
    spendingWallet.createSignedPsbtResult = Ok(appSignedPsbt)
    bitcoinWalletService.broadcastError = BdkError.Generic(Exception(""), null)

    stateMachine.testWithVirtualTime(
      props.copy(
        selectedPriority = transactionPriority
      )
    ) {
      // CreatingAppSignedPsbt
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      mobilePayService.getDailySpendingLimitStatusCalls.awaitItem().shouldBe(props.sendAmount)

      // ViewingTransferConfirmation
      awaitBody<FormBodyModel> {
        clickPrimaryButton()
      }

      // SigningWithHardware
      awaitBodyMock<NfcSessionUIStateMachineProps<Psbt>>(
        id = nfcSessionUIStateMachineId
      ) {
        onSuccess(appAndHwSignedPsbt)
      }

      // FinalizingAndBroadcastingTransaction
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      bitcoinWalletService.broadcastedPsbts.test {
        awaitItem().shouldContainExactly(appAndHwSignedPsbt)
      }

      // ReceivedBdkError
      awaitBody<FormBodyModel> {
        expectGenericErrorMessage()
        clickPrimaryButton()
      }
      onExitCalls.awaitItem()
    }

    transactionPriorityPreference.preference.shouldBeNull()
  }

  test("[app & server] successful signing syncs, broadcasts, calls onTransferInitiated") {
    val preferenceToSet = FASTEST
    spendingWallet.createSignedPsbtResult = Ok(appSignedPsbt)

    transactionPriorityPreference.preference.shouldBeNull()
    mobilePayService.mobilePayData.value = MobilePayEnabledDataMock
    mobilePayService.status = DailySpendingLimitStatus.MobilePayAvailable

    stateMachine.testWithVirtualTime(
      props.copy(
        selectedPriority = preferenceToSet
      )
    ) {
      // CreatingAppSignedPsbt
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      mobilePayService.getDailySpendingLimitStatusCalls.awaitItem().shouldBe(props.sendAmount)

      // ViewingTransferConfirmation
      awaitBody<FormBodyModel> {
        clickPrimaryButton()
      }

      // SigningWithServer
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      bitcoinWalletService.broadcastedPsbts.test {
        awaitItem().shouldContainExactly(mobilePayService.signPsbtCalls.awaitItem())
      }
    }

    transactionPriorityPreference.preference.shouldBe(preferenceToSet)
    onTransferInitiatedCalls.awaitItem()
  }

  test("[app & server] failure to sign with app key presents error") {
    val preferenceToSet = FASTEST
    spendingWallet.createSignedPsbtResult = Err(BdkError.Generic(Exception(""), null))
    mobilePayService.status = DailySpendingLimitStatus.MobilePayAvailable

    stateMachine.testWithVirtualTime(
      props.copy(
        selectedPriority = preferenceToSet
      )
    ) {
      // CreatingAppSignedPsbt
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      // ReceivedBdkError
      awaitBody<FormBodyModel> {
        expectGenericErrorMessage()
        clickPrimaryButton()
      }

      onExitCalls.awaitItem()
    }

    transactionPriorityPreference.preference.shouldBeNull()
  }

  test("[app & server] successfully signing, but failing to broadcast succeeds") {
    val preferenceToSet = FASTEST
    spendingWallet.createSignedPsbtResult = Ok(appSignedPsbt)
    bitcoinWalletService.broadcastError = BdkError.Generic(Exception(""), null)
    mobilePayService.keysetId =
      FullAccountMock.keybox.activeSpendingKeyset.f8eSpendingKeyset.keysetId
    mobilePayService.mobilePayData.value = MobilePayEnabledDataMock
    mobilePayService.status = DailySpendingLimitStatus.MobilePayAvailable

    stateMachine.testWithVirtualTime(
      props.copy(
        selectedPriority = preferenceToSet
      )
    ) {
      // CreatingAppSignedPsbt
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      mobilePayService.getDailySpendingLimitStatusCalls.awaitItem().shouldBe(props.sendAmount)

      // ViewingTransferConfirmation
      awaitBody<FormBodyModel> {
        clickPrimaryButton()
      }

      // SigningWithServer
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      bitcoinWalletService.broadcastedPsbts.test {
        awaitItem().shouldContainExactly(mobilePayService.signPsbtCalls.awaitItem())
      }
    }

    spendingWallet.syncCalls.awaitItem()
    transactionPriorityPreference.preference.shouldBe(preferenceToSet)
    onTransferInitiatedCalls.awaitItem()
  }

  test("[app & server] failure to sign with server key presents error") {
    val preferenceToSet = FASTEST
    spendingWallet.createSignedPsbtResult = Ok(appSignedPsbt)
    mobilePayService.signPsbtWithMobilePayResult = Err(NetworkError(Error("oops")))
    mobilePayService.mobilePayData.value = MobilePayEnabledDataMock
    mobilePayService.status = DailySpendingLimitStatus.MobilePayAvailable

    stateMachine.testWithVirtualTime(
      props.copy(
        selectedPriority = preferenceToSet
      )
    ) {
      // CreatingAppSignedPsbt
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      mobilePayService.getDailySpendingLimitStatusCalls.awaitItem().shouldBe(props.sendAmount)

      // ViewingTransferConfirmation
      awaitBody<FormBodyModel> {
        clickPrimaryButton()
      }

      // SigningWithServer
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      // ReceivedServerSigningError
      awaitBody<FormBodyModel> {
        with(header.shouldNotBeNull()) {
          headline.shouldBe("We couldn’t send this as a mobile-only transaction")
          sublineModel.shouldNotBeNull().string.shouldBe(
            "Please use your hardware device to confirm this transaction."
          )
        }
        with(primaryButton.shouldNotBeNull()) {
          text.shouldBe("Continue")
          onClick()
        }
      }

      // SigningWithHardware
      awaitBodyMock<NfcSessionUIStateMachineProps<Psbt>>(
        id = nfcSessionUIStateMachineId
      )
    }

    mobilePayService.signPsbtCalls.awaitItem()

    transactionPriorityPreference.preference.shouldBeNull()
  }
}

fun FormBodyModel.expectGenericErrorMessage() {
  with(header.shouldNotBeNull()) {
    headline.shouldBe("We couldn’t send this transaction")
    sublineModel.shouldNotBeNull().string.shouldBe(
      "We are looking into this. Please try again later."
    )
  }
  with(primaryButton.shouldNotBeNull()) {
    text.shouldBe("Done")
  }
}
