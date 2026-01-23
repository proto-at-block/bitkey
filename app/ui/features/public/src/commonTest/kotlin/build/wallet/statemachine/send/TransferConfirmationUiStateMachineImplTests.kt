package build.wallet.statemachine.send

import app.cash.turbine.Turbine
import app.cash.turbine.test
import bitkey.verification.ConfirmationState
import bitkey.verification.FakeTxVerificationApproval
import bitkey.verification.TxVerificationServiceFake
import build.wallet.analytics.events.screen.id.TxVerificationEventTrackerScreenId
import build.wallet.bdk.bindings.BdkError
import build.wallet.bitcoin.transactions.BitcoinWalletServiceFake
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority.FASTEST
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitcoin.transactions.TransactionPriorityPreferenceFake
import build.wallet.bitcoin.wallet.SpendingWalletMock
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.coroutines.turbine.awaitUntil
import build.wallet.feature.flags.TxVerificationFeatureFlag
import build.wallet.feature.setFlagValue
import build.wallet.ktor.result.HttpError.NetworkError
import build.wallet.limit.DailySpendingLimitStatus
import build.wallet.limit.MobilePayDisabledDataMock
import build.wallet.limit.MobilePayEnabledDataMock
import build.wallet.limit.MobilePayServiceMock
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.nfc.NfcConfirmableSessionUIStateMachineProps
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitBodyMock
import build.wallet.statemachine.ui.clickPrimaryButton
import build.wallet.statemachine.ui.robots.click
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.flowOf

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
  txVerificationServiceFake: TxVerificationServiceFake,
  verificationFlag: TxVerificationFeatureFlag,
) {
  test("onBack from TransferConfirmationScreenModel invokes props.onBack") {
    stateMachine.test(props) {
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      mobilePayService.getDailySpendingLimitStatusCalls.awaitItem()

      awaitBody<LoadingSuccessBodyModel>()

      awaitBody<TransferConfirmationScreenModel> {
        onBack()
      }

      onBackCalls.awaitItem()
    }
  }

  test("create unsigned psbt error - insufficient funds") {
    spendingWallet.createSignedPsbtResult =
      Err(BdkError.InsufficientFunds(Exception(""), null))

    stateMachine.test(props) {
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

    stateMachine.test(props) {
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

    stateMachine.test(props) {
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

    stateMachine.test(
      props.copy(
        selectedPriority = transactionPriority
      )
    ) {
      // CreatingAppSignedPsbt
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      mobilePayService.getDailySpendingLimitStatusCalls.awaitItem().shouldBe(props.sendAmount)

      awaitBody<LoadingSuccessBodyModel>()

      // ViewingTransferConfirmation
      awaitBody<FormBodyModel> {
        clickPrimaryButton()
      }

      // SigningWithHardware
      awaitBodyMock<NfcConfirmableSessionUIStateMachineProps<Psbt>>(
        id = nfcSessionUIStateMachineId
      ) {
        onSuccess(appAndHwSignedPsbt)
      }

      // FinalizingAndBroadcastingTransaction
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      bitcoinWalletService.broadcastedPsbts.test {
        awaitUntil { it.isNotEmpty() }.shouldContainExactly(appAndHwSignedPsbt)
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

    stateMachine.test(
      props.copy(
        selectedPriority = preferenceToSet
      )
    ) {
      // CreatingAppSignedPsbt
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      mobilePayService.getDailySpendingLimitStatusCalls.awaitItem().shouldBe(props.sendAmount)

      awaitBody<LoadingSuccessBodyModel>()

      // ViewingTransferConfirmation
      awaitBody<FormBodyModel> {
        clickPrimaryButton()
      }

      // SigningWithServer
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      mobilePayService.signPsbtGrants.awaitItem().shouldBeNull()

      bitcoinWalletService.broadcastedPsbts.test {
        awaitUntil { it.isNotEmpty() }.shouldContainExactly(mobilePayService.signPsbtCalls.awaitItem())
      }
    }

    transactionPriorityPreference.preference.shouldBe(preferenceToSet)
    onTransferInitiatedCalls.awaitItem()
  }

  test("[App+Server w/Verification] successful signing syncs, broadcasts, calls onTransferInitiated") {
    verificationFlag.setFlagValue(true)
    val preferenceToSet = FASTEST
    spendingWallet.createSignedPsbtResult = Ok(appSignedPsbt)
    txVerificationServiceFake.requireVerification = true

    transactionPriorityPreference.preference.shouldBeNull()
    mobilePayService.mobilePayData.value = MobilePayEnabledDataMock
    mobilePayService.status = DailySpendingLimitStatus.MobilePayAvailable

    stateMachine.test(
      props.copy(
        selectedPriority = preferenceToSet
      )
    ) {
      // CreatingAppSignedPsbt
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      awaitBody<LoadingSuccessBodyModel>()

      awaitBody<VerifyConfirmation> {
        onContinue()
      }

      mobilePayService.getDailySpendingLimitStatusCalls.awaitItem().shouldBe(props.sendAmount)

      awaitBody<LoadingSuccessBodyModel>()

      // ViewingTransferConfirmation
      awaitBody<FormBodyModel> {
        clickPrimaryButton()
      }

      // SigningWithServer
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      mobilePayService.signPsbtGrants.awaitItem().shouldBe(FakeTxVerificationApproval)

      bitcoinWalletService.broadcastedPsbts.test {
        awaitUntil { it.isNotEmpty() }.shouldContainExactly(mobilePayService.signPsbtCalls.awaitItem())
      }
    }

    transactionPriorityPreference.preference.shouldBe(preferenceToSet)
    onTransferInitiatedCalls.awaitItem()
  }
  test("Verification cancels") {
    verificationFlag.setFlagValue(true)
    val preferenceToSet = FASTEST
    spendingWallet.createSignedPsbtResult = Ok(appSignedPsbt)
    txVerificationServiceFake.requireVerification = true
    txVerificationServiceFake.verificationResult = Ok(
      flowOf(ConfirmationState.Pending)
    )

    transactionPriorityPreference.preference.shouldBeNull()
    mobilePayService.mobilePayData.value = MobilePayEnabledDataMock
    mobilePayService.status = DailySpendingLimitStatus.MobilePayAvailable

    stateMachine.test(
      props.copy(
        selectedPriority = preferenceToSet
      )
    ) {
      // CreatingAppSignedPsbt
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      awaitBody<LoadingSuccessBodyModel>()

      awaitBody<VerifyConfirmation> {
        onContinue()
      }

      awaitBody<LoadingSuccessBodyModel> {
        secondaryButton.shouldNotBeNull().click()
      }

      awaitBody<TransactionCanceledBodyModel>(TxVerificationEventTrackerScreenId.VERIFICATION_CANCELED) {
        onExit()
      }
      onBackCalls.awaitItem()
    }
  }

  test("Verification retry returns to VerifyConfirmation") {
    verificationFlag.setFlagValue(true)
    val preferenceToSet = FASTEST
    spendingWallet.createSignedPsbtResult = Ok(appSignedPsbt)
    txVerificationServiceFake.requireVerification = true
    txVerificationServiceFake.verificationResult = Ok(
      flowOf(ConfirmationState.Pending)
    )

    transactionPriorityPreference.preference.shouldBeNull()
    mobilePayService.mobilePayData.value = MobilePayEnabledDataMock
    mobilePayService.status = DailySpendingLimitStatus.MobilePayAvailable

    stateMachine.test(
      props.copy(
        selectedPriority = preferenceToSet
      )
    ) {
      // CreatingAppSignedPsbt
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      awaitBody<LoadingSuccessBodyModel>()

      awaitBody<VerifyConfirmation> {
        onContinue()
      }

      awaitBody<LoadingSuccessBodyModel> {
        primaryButton.shouldNotBeNull().click()
      }

      awaitBody<VerifyConfirmation>()
    }
  }

  test("[App+HW w/Verification] successful signing syncs, broadcasts, calls onTransferInitiated") {
    verificationFlag.setFlagValue(true)
    val preferenceToSet = FASTEST
    spendingWallet.createSignedPsbtResult = Ok(appSignedPsbt)
    txVerificationServiceFake.requireVerification = true

    transactionPriorityPreference.preference.shouldBeNull()
    mobilePayService.mobilePayData.value = MobilePayDisabledDataMock
    mobilePayService.status = DailySpendingLimitStatus.MobilePayAvailable

    stateMachine.test(
      props.copy(
        selectedPriority = preferenceToSet
      )
    ) {
      // CreatingAppSignedPsbt
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      awaitBody<LoadingSuccessBodyModel>()

      awaitBody<VerifyConfirmation> {
        onContinue()
      }

      mobilePayService.getDailySpendingLimitStatusCalls.awaitItem().shouldBe(props.sendAmount)

      awaitBody<LoadingSuccessBodyModel>()

      // ViewingTransferConfirmation
      awaitBody<FormBodyModel> {
        clickPrimaryButton()
      }

      // SigningWithHardware
      awaitBodyMock<NfcConfirmableSessionUIStateMachineProps<Psbt>>(
        id = nfcSessionUIStateMachineId
      ) {
        onSuccess(appAndHwSignedPsbt)
      }

      // FinalizingAndBroadcastingTransaction
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      bitcoinWalletService.broadcastedPsbts.test {
        awaitUntil { it.isNotEmpty() }.shouldContainExactly(appAndHwSignedPsbt)
      }
    }

    transactionPriorityPreference.preference.shouldBe(preferenceToSet)
    onTransferInitiatedCalls.awaitItem()
  }

  test("[App+Server w/Direct Grant] successful signing syncs, broadcasts, calls onTransferInitiated") {
    verificationFlag.setFlagValue(true)
    val preferenceToSet = FASTEST
    spendingWallet.createSignedPsbtResult = Ok(appSignedPsbt)
    txVerificationServiceFake.requireVerification = false

    transactionPriorityPreference.preference.shouldBeNull()
    mobilePayService.mobilePayData.value = MobilePayEnabledDataMock
    mobilePayService.status = DailySpendingLimitStatus.MobilePayAvailable

    stateMachine.test(
      props.copy(
        selectedPriority = preferenceToSet
      )
    ) {
      // CreatingAppSignedPsbt
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      awaitBody<LoadingSuccessBodyModel>()

      mobilePayService.getDailySpendingLimitStatusCalls.awaitItem().shouldBe(props.sendAmount)

      // ViewingTransferConfirmation
      awaitBody<FormBodyModel> {
        clickPrimaryButton()
      }

      // SigningWithServer
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      mobilePayService.signPsbtGrants.awaitItem().shouldBe(FakeTxVerificationApproval)

      bitcoinWalletService.broadcastedPsbts.test {
        awaitUntil { it.isNotEmpty() }.shouldContainExactly(mobilePayService.signPsbtCalls.awaitItem())
      }
    }

    transactionPriorityPreference.preference.shouldBe(preferenceToSet)
    onTransferInitiatedCalls.awaitItem()
  }

  test("[App+HW w/Direct Grant] successful signing syncs, broadcasts, calls onTransferInitiated") {
    verificationFlag.setFlagValue(true)
    val preferenceToSet = FASTEST
    spendingWallet.createSignedPsbtResult = Ok(appSignedPsbt)
    txVerificationServiceFake.requireVerification = false

    transactionPriorityPreference.preference.shouldBeNull()
    mobilePayService.mobilePayData.value = MobilePayDisabledDataMock
    mobilePayService.status = DailySpendingLimitStatus.MobilePayAvailable

    stateMachine.test(
      props.copy(
        selectedPriority = preferenceToSet
      )
    ) {
      // CreatingAppSignedPsbt
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      awaitBody<LoadingSuccessBodyModel>()

      mobilePayService.getDailySpendingLimitStatusCalls.awaitItem().shouldBe(props.sendAmount)

      // ViewingTransferConfirmation
      awaitBody<FormBodyModel> {
        clickPrimaryButton()
      }

      // SigningWithHardware
      awaitBodyMock<NfcConfirmableSessionUIStateMachineProps<Psbt>>(
        id = nfcSessionUIStateMachineId
      ) {
        onSuccess(appAndHwSignedPsbt)
      }

      // FinalizingAndBroadcastingTransaction
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      bitcoinWalletService.broadcastedPsbts.test {
        awaitUntil { it.isNotEmpty() }.shouldContainExactly(appAndHwSignedPsbt)
      }
    }

    transactionPriorityPreference.preference.shouldBe(preferenceToSet)
    onTransferInitiatedCalls.awaitItem()
  }

  test("Verification canceled") {
    verificationFlag.setFlagValue(true)
    val preferenceToSet = FASTEST
    spendingWallet.createSignedPsbtResult = Ok(appSignedPsbt)
    txVerificationServiceFake.requireVerification = true
    txVerificationServiceFake.verificationResult = Ok(
      flowOf(
        ConfirmationState.Pending,
        ConfirmationState.Rejected
      )
    )

    transactionPriorityPreference.preference.shouldBeNull()
    mobilePayService.mobilePayData.value = MobilePayEnabledDataMock
    mobilePayService.status = DailySpendingLimitStatus.MobilePayAvailable

    stateMachine.test(
      props.copy(
        selectedPriority = preferenceToSet
      )
    ) {
      // CreatingAppSignedPsbt
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      awaitBody<LoadingSuccessBodyModel>()

      awaitBody<VerifyConfirmation> {
        onContinue()
      }

      awaitBody<LoadingSuccessBodyModel>()

      awaitBody<TransactionCanceledBodyModel>(TxVerificationEventTrackerScreenId.VERIFICATION_REJECTED) {
        onExit()
      }
    }
    onBackCalls.awaitItem()
  }

  test("Verification expired") {
    verificationFlag.setFlagValue(true)
    val preferenceToSet = FASTEST
    spendingWallet.createSignedPsbtResult = Ok(appSignedPsbt)
    txVerificationServiceFake.requireVerification = true
    txVerificationServiceFake.verificationResult = Ok(
      flowOf(
        ConfirmationState.Pending,
        ConfirmationState.Expired
      )
    )

    transactionPriorityPreference.preference.shouldBeNull()
    mobilePayService.mobilePayData.value = MobilePayEnabledDataMock
    mobilePayService.status = DailySpendingLimitStatus.MobilePayAvailable

    stateMachine.test(
      props.copy(
        selectedPriority = preferenceToSet
      )
    ) {
      // CreatingAppSignedPsbt
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      awaitBody<LoadingSuccessBodyModel>()

      awaitBody<VerifyConfirmation> {
        onContinue()
      }

      awaitBody<LoadingSuccessBodyModel>()

      awaitBody<TransactionCanceledBodyModel>(TxVerificationEventTrackerScreenId.VERIFICATION_EXPIRED) {
        onExit()
      }
    }
    onBackCalls.awaitItem()
  }

  test("[app & server] failure to sign with app key presents error") {
    val preferenceToSet = FASTEST
    spendingWallet.createSignedPsbtResult = Err(BdkError.Generic(Exception(""), null))
    mobilePayService.status = DailySpendingLimitStatus.MobilePayAvailable

    stateMachine.test(
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

    stateMachine.test(
      props.copy(
        selectedPriority = preferenceToSet
      )
    ) {
      // CreatingAppSignedPsbt
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      mobilePayService.getDailySpendingLimitStatusCalls.awaitItem().shouldBe(props.sendAmount)

      awaitBody<LoadingSuccessBodyModel>()

      // ViewingTransferConfirmation
      awaitBody<FormBodyModel> {
        clickPrimaryButton()
      }

      // SigningWithServer
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      mobilePayService.signPsbtGrants.awaitItem().shouldBeNull()

      bitcoinWalletService.broadcastedPsbts.test {
        awaitUntil { it.isNotEmpty() }.shouldContainExactly(mobilePayService.signPsbtCalls.awaitItem())
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

    stateMachine.test(
      props.copy(
        selectedPriority = preferenceToSet
      )
    ) {
      // CreatingAppSignedPsbt
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      mobilePayService.getDailySpendingLimitStatusCalls.awaitItem().shouldBe(props.sendAmount)

      awaitBody<LoadingSuccessBodyModel>()

      // ViewingTransferConfirmation
      awaitBody<FormBodyModel> {
        clickPrimaryButton()
      }

      // SigningWithServer
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      mobilePayService.signPsbtGrants.awaitItem().shouldBeNull()

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
      awaitBodyMock<NfcConfirmableSessionUIStateMachineProps<Psbt>>(
        id = nfcSessionUIStateMachineId
      )
    }

    mobilePayService.signPsbtCalls.awaitItem()

    transactionPriorityPreference.preference.shouldBeNull()
  }

  test("broadcast failure after server-to-hardware fallback shows error instead of success") {
    val preferenceToSet = FASTEST
    spendingWallet.createSignedPsbtResult = Ok(appSignedPsbt)
    mobilePayService.signPsbtWithMobilePayResult = Err(NetworkError(Error("fee too low")))
    mobilePayService.mobilePayData.value = MobilePayEnabledDataMock
    mobilePayService.status = DailySpendingLimitStatus.MobilePayAvailable
    bitcoinWalletService.broadcastError = BdkError.Electrum(Exception(""), null)

    stateMachine.test(
      props.copy(
        selectedPriority = preferenceToSet
      )
    ) {
      // CreatingAppSignedPsbt
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      mobilePayService.getDailySpendingLimitStatusCalls.awaitItem().shouldBe(props.sendAmount)

      awaitBody<LoadingSuccessBodyModel>()

      // ViewingTransferConfirmation
      awaitBody<FormBodyModel> {
        clickPrimaryButton()
      }

      // SigningWithServer (fails)
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      mobilePayService.signPsbtGrants.awaitItem().shouldBeNull()

      // ReceivedServerSigningError - user clicks Continue to fallback to hardware
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

      // SigningWithHardware (succeeds)
      awaitBodyMock<NfcConfirmableSessionUIStateMachineProps<Psbt>>(
        id = nfcSessionUIStateMachineId
      ) {
        onSuccess(appAndHwSignedPsbt)
      }

      // BroadcastingTransaction (fails)
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      bitcoinWalletService.broadcastedPsbts.test {
        awaitUntil { it.isNotEmpty() }.shouldContainExactly(appAndHwSignedPsbt)
      }

      // Should show error screen
      awaitBody<FormBodyModel> {
        expectGenericErrorMessage()
        clickPrimaryButton()
      }
      onExitCalls.awaitItem()
    }

    mobilePayService.signPsbtCalls.awaitItem()

    // Transaction priority should not be set since broadcast failed
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
