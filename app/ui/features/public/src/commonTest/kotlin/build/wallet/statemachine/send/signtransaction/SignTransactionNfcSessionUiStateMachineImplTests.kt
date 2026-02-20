package build.wallet.statemachine.send.signtransaction

import bitkey.account.AccountConfigServiceFake
import bitkey.account.HardwareType
import build.wallet.account.AccountServiceFake
import build.wallet.analytics.events.EventTrackerMock
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitcoin.transactions.PsbtMock
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.encrypt.SignatureVerifierMock
import build.wallet.encrypt.SignatureVerifierMock.VerifyEcdsaCall
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.flags.NfcSessionRetryAttemptsFeatureFlag
import build.wallet.keybox.KeyboxDaoMock
import build.wallet.nfc.*
import build.wallet.platform.web.InAppBrowserNavigatorMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.core.test
import build.wallet.statemachine.platform.nfc.EnableNfcNavigatorMock
import build.wallet.statemachine.send.hardwareconfirmation.HardwareConfirmationUiProps
import build.wallet.statemachine.send.hardwareconfirmation.HardwareConfirmationUiStateMachine
import build.wallet.statemachine.send.signtransaction.SignTransactionNfcBodyModel.Status.*
import build.wallet.statemachine.ui.awaitBody
import com.github.michaelbull.result.Err
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import kotlinx.coroutines.runBlocking

/**
 * Tests for [SignTransactionNfcSessionUiStateMachineImpl].
 *
 * Note: Due to [SignTransactionResult] being private to the implementation,
 * these tests focus on error handling and NFC session parameter validation.
 * Full W1/W3 flow testing would require either making SignTransactionResult public
 * or integration testing with real NFC commands.
 */
class SignTransactionNfcSessionUiStateMachineImplTests : FunSpec({

  val eventTracker = EventTrackerMock(turbines::create)
  val nfcTransactor = NfcTransactorMock(turbines::create)
  val accountConfigService = AccountConfigServiceFake()
  val accountService = AccountServiceFake()
  val keyboxDao = KeyboxDaoMock(turbines::create)
  val signatureVerifierTurbine = turbines.create<VerifyEcdsaCall>("verifyEcdsa calls")
  val nfcSessionRetryAttemptsFeatureFlag = NfcSessionRetryAttemptsFeatureFlag(FeatureFlagDaoFake())
  val hardwareConfirmationUiStateMachine =
    object : HardwareConfirmationUiStateMachine,
      ScreenStateMachineMock<HardwareConfirmationUiProps>("hardware-confirmation") {}

  val stateMachine =
    SignTransactionNfcSessionUiStateMachineImpl(
      enableNfcNavigator = EnableNfcNavigatorMock(),
      eventTracker = eventTracker,
      nfcReaderCapability = NfcReaderCapabilityMock(),
      nfcTransactor = nfcTransactor,
      accountConfigService = accountConfigService,
      accountService = accountService,
      keyboxDao = keyboxDao,
      signatureVerifier = SignatureVerifierMock(signatureVerifierTurbine),
      nfcSessionRetryAttemptsFeatureFlag = nfcSessionRetryAttemptsFeatureFlag,
      hardwareConfirmationUiStateMachine = hardwareConfirmationUiStateMachine,
      inAppBrowserNavigator = InAppBrowserNavigatorMock(turbines::create)
    )

  val onBackCalls = turbines.create<Unit>("onBack calls")
  val onSuccessCalls = turbines.create<Psbt>("onSuccess calls")
  val onErrorCalls = turbines.create<NfcException>("onError calls")

  val props =
    SignTransactionNfcSessionUiProps(
      psbt = PsbtMock,
      onBack = { onBackCalls.add(Unit) },
      onSuccess = { psbt -> onSuccessCalls.add(psbt) },
      onError = { error ->
        onErrorCalls.add(error)
        true // Handled - don't show default error UI
      }
    )

  beforeTest {
    accountConfigService.reset()
    accountService.reset()
    accountService.setActiveAccount(FullAccountMock)
    nfcTransactor.reset()
    keyboxDao.reset()
  }

  // Basic UI State Tests

  test("initial state shows searching") {
    // Set to never complete so we can just verify initial state
    nfcTransactor.transactResult = Err(NfcException.Timeout())

    stateMachine.test(props) {
      // Initially shows searching state
      awaitBody<SignTransactionNfcBodyModel> {
        status.shouldBeTypeOf<Searching>()
        onCancel.shouldNotBeNull()
      }

      nfcTransactor.transactCalls.awaitItem()
      onErrorCalls.awaitItem()
    }
  }

  test("cancel button invokes onBack") {
    nfcTransactor.transactResult = Err(NfcException.Timeout())

    stateMachine.test(props) {
      awaitBody<SignTransactionNfcBodyModel> {
        status.shouldBeTypeOf<Searching>()
        onCancel.shouldNotBeNull().invoke()
      }

      nfcTransactor.transactCalls.awaitItem()
      onErrorCalls.awaitItem()
      onBackCalls.awaitItem()
    }
  }

  // Error Handling Tests

  test("user cancellation on iOS invokes onBack") {
    nfcTransactor.transactResult = Err(NfcException.IOSOnly.UserCancellation())

    stateMachine.test(props) {
      awaitBody<SignTransactionNfcBodyModel> {
        status.shouldBeTypeOf<Searching>()
      }

      nfcTransactor.transactCalls.awaitItem()
      onBackCalls.awaitItem()
    }
  }

  test("command error invokes onError") {
    nfcTransactor.transactResult = Err(NfcException.CommandError("Test error"))

    stateMachine.test(props) {
      awaitBody<SignTransactionNfcBodyModel> {
        status.shouldBeTypeOf<Searching>()
      }

      nfcTransactor.transactCalls.awaitItem()
      onErrorCalls.awaitItem().shouldBeTypeOf<NfcException.CommandError>()
    }
  }

  test("tag lost error invokes onError") {
    nfcTransactor.transactResult = Err(NfcException.CanBeRetried.TagLost())

    stateMachine.test(props) {
      awaitBody<SignTransactionNfcBodyModel> {
        status.shouldBeTypeOf<Searching>()
      }

      nfcTransactor.transactCalls.awaitItem()
      onErrorCalls.awaitItem().shouldBeTypeOf<NfcException.CanBeRetried.TagLost>()
    }
  }

  test("timeout error invokes onError") {
    nfcTransactor.transactResult = Err(NfcException.Timeout())

    stateMachine.test(props) {
      awaitBody<SignTransactionNfcBodyModel> {
        status.shouldBeTypeOf<Searching>()
      }

      nfcTransactor.transactCalls.awaitItem()
      onErrorCalls.awaitItem().shouldBeTypeOf<NfcException.Timeout>()
    }
  }

  test("unauthenticated error invokes onError") {
    nfcTransactor.transactResult = Err(NfcException.CommandErrorUnauthenticated())

    stateMachine.test(props) {
      awaitBody<SignTransactionNfcBodyModel> {
        status.shouldBeTypeOf<Searching>()
      }

      nfcTransactor.transactCalls.awaitItem()
      onErrorCalls.awaitItem().shouldBeTypeOf<NfcException.CommandErrorUnauthenticated>()
    }
  }

  // NFC Session Parameters Tests

  test("NFC session parameters are correctly configured for W1") {
    runBlocking {
      accountConfigService.setHardwareType(HardwareType.W1)
    }
    nfcTransactor.transactResult = Err(NfcException.Timeout())

    stateMachine.test(props) {
      awaitBody<SignTransactionNfcBodyModel>()

      val transactCalls = nfcTransactor.transactCalls.awaitItem()
        .shouldBeTypeOf<NfcSession.Parameters>()

      transactCalls.hardwareType.shouldBe(HardwareType.W1)
      transactCalls.needsAuthentication.shouldBe(true)
      transactCalls.shouldLock.shouldBe(true)
      transactCalls.skipFirmwareTelemetry.shouldBe(false)
      transactCalls.nfcFlowName.shouldBe("sign-transaction")
      transactCalls.asyncNfcSigning.shouldBe(false)

      onErrorCalls.awaitItem()
    }
  }

  test("NFC session parameters are correctly configured for W3") {
    runBlocking {
      accountConfigService.setHardwareType(HardwareType.W3)
    }
    nfcTransactor.transactResult = Err(NfcException.Timeout())

    stateMachine.test(props) {
      awaitBody<SignTransactionNfcBodyModel>()

      val transactCalls = nfcTransactor.transactCalls.awaitItem()
        .shouldBeTypeOf<NfcSession.Parameters>()

      transactCalls.hardwareType.shouldBe(HardwareType.W3)
      transactCalls.needsAuthentication.shouldBe(true)
      transactCalls.shouldLock.shouldBe(true)
      transactCalls.skipFirmwareTelemetry.shouldBe(false)
      transactCalls.nfcFlowName.shouldBe("sign-transaction")
      transactCalls.asyncNfcSigning.shouldBe(false)

      onErrorCalls.awaitItem()
    }
  }

  test("NFC session uses default W1 hardware type when no config") {
    // Don't set any hardware type
    nfcTransactor.transactResult = Err(NfcException.Timeout())

    stateMachine.test(props) {
      awaitBody<SignTransactionNfcBodyModel>()

      val transactCalls = nfcTransactor.transactCalls.awaitItem()
        .shouldBeTypeOf<NfcSession.Parameters>()

      transactCalls.hardwareType.shouldBe(HardwareType.W1)

      onErrorCalls.awaitItem()
    }
  }

  test("default error handling shows NFC error UI") {
    nfcTransactor.transactResult = Err(NfcException.CommandError("Test error"))

    val propsWithDefaultErrorHandler =
      SignTransactionNfcSessionUiProps(
        psbt = PsbtMock,
        onBack = { onBackCalls.add(Unit) },
        onSuccess = { psbt -> onSuccessCalls.add(psbt) }
        // onError not provided - uses default (returns false)
      )

    stateMachine.test(propsWithDefaultErrorHandler) {
      awaitBody<SignTransactionNfcBodyModel>()

      nfcTransactor.transactCalls.awaitItem()

      // Should show error UI (NfcErrorFormBodyModel)
      awaitBody<build.wallet.statemachine.core.form.FormBodyModel>()
    }
  }
})
