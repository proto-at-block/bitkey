package build.wallet.statemachine.nfc

import bitkey.account.AccountConfigServiceFake
import build.wallet.account.AccountServiceFake
import build.wallet.account.AccountStatus
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext.UTXO_CONSOLIDATION_SIGN_TRANSACTION
import build.wallet.analytics.events.screen.id.NfcEventTrackerScreenId
import build.wallet.bitkey.auth.AppGlobalAuthKeyHwSignatureMock
import build.wallet.bitkey.auth.AppGlobalAuthPublicKeyMock
import build.wallet.bitkey.auth.AppRecoveryAuthPublicKeyMock
import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.bitkey.factor.PhysicalFactor.Hardware
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.keybox.EekKeyboxMock
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.spending.AppSpendingPublicKeyMock
import build.wallet.bitkey.spending.HwSpendingPublicKeyMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.encrypt.Secp256k1PublicKey
import build.wallet.encrypt.SignatureVerifierMock
import build.wallet.encrypt.SignatureVerifierMock.VerifyEcdsaCall
import build.wallet.f8e.recovery.LostHardwareServerRecoveryMock
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.flags.AsyncNfcSigningFeatureFlag
import build.wallet.feature.flags.CheckHardwareIsPairedFeatureFlag
import build.wallet.feature.flags.NfcSessionRetryAttemptsFeatureFlag
import build.wallet.feature.setFlagValue
import build.wallet.nfc.NfcAvailability
import build.wallet.nfc.NfcException
import build.wallet.nfc.NfcReaderCapabilityMock
import build.wallet.nfc.NfcSession.Parameters
import build.wallet.nfc.NfcSession.RequirePairedHardware
import build.wallet.nfc.NfcTransactorMock
import build.wallet.platform.config.AppVariant
import build.wallet.platform.device.DeviceInfoProviderMock
import build.wallet.platform.web.InAppBrowserNavigatorMock
import build.wallet.recovery.Recovery.StillRecovering.ServerDependentRecovery.InitiatedRecovery
import build.wallet.recovery.RecoveryStatusServiceMock
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.nfc.NfcBodyModel.Status.Searching
import build.wallet.statemachine.nfc.NfcBodyModel.Status.Success
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine.Companion.TROUBLESHOOTING_URL
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps.HardwareVerification
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps.HardwareVerification.NotRequired
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps.HardwareVerification.Required
import build.wallet.statemachine.platform.nfc.EnableNfcNavigatorMock
import build.wallet.statemachine.ui.awaitBody
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import okio.ByteString.Companion.encodeUtf8

class NfcSessionUIStateMachineImplTests : FunSpec({

  val deviceInfoProvider = DeviceInfoProviderMock()
  val nfcReaderCapability = NfcReaderCapabilityMock()
  val nfcTransactor = NfcTransactorMock(turbines::create)
  val enableNfcNavigator = EnableNfcNavigatorMock()
  val asyncNfcSigningFeatureFlag = AsyncNfcSigningFeatureFlag(FeatureFlagDaoFake())
  val accountConfigService = AccountConfigServiceFake()
  val signatureVerifyCalls = turbines.create<VerifyEcdsaCall>("verifyEcdsa calls")
  val signatureVerifier = SignatureVerifierMock(signatureVerifyCalls)
  val accountService = AccountServiceFake()
  val checkHardwareIsPairedFeatureFlag = CheckHardwareIsPairedFeatureFlag(FeatureFlagDaoFake())
  val nfcSessionRetryAttemptsFeatureFlag = NfcSessionRetryAttemptsFeatureFlag(FeatureFlagDaoFake())
  val recoveryStatusService = RecoveryStatusServiceMock(turbine = turbines::create)
  val inAppBrowserNavigator = InAppBrowserNavigatorMock(turbines::create)

  val stateMachine = NfcSessionUIStateMachineImpl(
    nfcReaderCapability = nfcReaderCapability,
    enableNfcNavigator = enableNfcNavigator,
    deviceInfoProvider = deviceInfoProvider,
    nfcTransactor = nfcTransactor,
    asyncNfcSigningFeatureFlag = asyncNfcSigningFeatureFlag,
    accountConfigService = accountConfigService,
    signatureVerifier = signatureVerifier,
    accountService = accountService,
    checkHardwareIsPairedFeatureFlag = checkHardwareIsPairedFeatureFlag,
    inAppBrowserNavigator = inAppBrowserNavigator,
    nfcSessionRetryAttemptsFeatureFlag = nfcSessionRetryAttemptsFeatureFlag,
    recoveryStatusService = recoveryStatusService,
    appVariant = AppVariant.Customer
  )

  val onCancelCalls = turbines.create<Unit>("onCancel calls")
  val onSuccessCalls = turbines.create<Unit>("onSuccess calls")

  fun createProps(
    requirePairedHardware: HardwareVerification = NotRequired,
    shouldShowLongRunningOperation: Boolean = false,
    onError: (NfcException) -> Boolean = { false },
  ) = NfcSessionUIStateMachineProps<Unit>(
    session = { _, _ -> },
    onConnected = {},
    onSuccess = { onSuccessCalls.add(Unit) },
    onCancel = { onCancelCalls.add(Unit) },
    onInauthenticHardware = { _ -> },
    onError = onError,
    needsAuthentication = true,
    hardwareVerification = requirePairedHardware,
    shouldLock = true,
    segment = null,
    actionDescription = null,
    screenPresentationStyle = ScreenPresentationStyle.FullScreen,
    eventTrackerContext = UTXO_CONSOLIDATION_SIGN_TRANSACTION,
    shouldShowLongRunningOperation = shouldShowLongRunningOperation
  )

  val props = createProps()

  beforeTest {
    accountConfigService.reset()
    deviceInfoProvider.reset()
    nfcReaderCapability.reset()
    nfcTransactor.reset()
    accountService.reset()
    signatureVerifier.reset()
    inAppBrowserNavigator.reset()
  }

  test("happy path") {
    nfcTransactor.transactResult = Ok(Unit)
    stateMachine.test(props) {
      awaitBody<NfcBodyModel> {
        text.shouldBe("Hold device here behind phone")
        status.shouldBeTypeOf<Searching>()
      }

      nfcTransactor.transactCalls.awaitItem()
      onSuccessCalls.awaitItem()

      awaitBody<NfcBodyModel> {
        text.shouldBe("Success")
        status.shouldBeTypeOf<Success>()
      }
    }
  }

  test("error path") {
    val error = NfcException.CommandError()
    nfcTransactor.transactResult = Err(error)
    stateMachine.test(props) {
      awaitBody<NfcBodyModel> {
        text.shouldBe("Hold device here behind phone")
        status.shouldBeTypeOf<Searching>()
      }
      nfcTransactor.transactCalls.awaitItem()

      awaitBody<FormBodyModel>(NfcEventTrackerScreenId.NFC_FAILURE)
    }
  }

  test("error callback overrides default handling") {
    val error = NfcException.CommandError()
    nfcTransactor.transactResult = Err(error)
    val errorCalls = turbines.create<Unit>("error calls")
    val propsWithCallback = createProps(onError = {
      errorCalls.add(Unit)
      true
    })

    stateMachine.test(propsWithCallback) {
      awaitBody<NfcBodyModel> { status.shouldBeTypeOf<Searching>() }
      nfcTransactor.transactCalls.awaitItem()
      errorCalls.awaitItem()
    }
  }

  test("troubleshooting guide opens in browser when secondary button clicked") {
    val error = NfcException.CommandError()
    nfcTransactor.transactResult = Err(error)
    stateMachine.test(props) {
      awaitBody<NfcBodyModel> {
        text.shouldBe("Hold device here behind phone")
        status.shouldBeTypeOf<Searching>()
      }
      nfcTransactor.transactCalls.awaitItem()

      awaitBody<FormBodyModel>(NfcEventTrackerScreenId.NFC_FAILURE) {
        secondaryButton!!.text.shouldBe("View troubleshooting guide")

        // Click the secondary button to open troubleshooting guide
        secondaryButton!!.onClick()

        // Verify the troubleshooting URL was opened in the browser
        val openedUrl = inAppBrowserNavigator.onOpenCalls.awaitItem()
        openedUrl.shouldBe(TROUBLESHOOTING_URL)
      }
    }
  }

  test("hardware pairing check - feature flag enabled - paired hardware") {
    checkHardwareIsPairedFeatureFlag.setFlagValue(true)
    val propsWithPairing = createProps(requirePairedHardware = Required())

    accountService.accountState.value = Ok(AccountStatus.ActiveAccount(FullAccountMock))

    nfcTransactor.transactResult = Ok(Unit)
    stateMachine.test(propsWithPairing) {
      awaitBody<NfcBodyModel> {
        text.shouldBe("Hold device here behind phone")
        status.shouldBeTypeOf<Searching>()
      }

      // Verify pairing check is enabled and the account's pubkey is used
      val transactCalls =
        nfcTransactor.transactCalls.awaitItem()
          .shouldBeTypeOf<Parameters>()
      transactCalls.requirePairedHardware.shouldBeTypeOf<RequirePairedHardware.Required>()
        .checkHardwareIsPaired("signature", "challenge".encodeUtf8())

      signatureVerifyCalls.awaitItem()
        .publicKey.shouldBe(FullAccountMock.keybox.activeHwKeyBundle.authKey.pubKey)

      onSuccessCalls.awaitItem()
      awaitBody<NfcBodyModel> {
        status.shouldBeTypeOf<Success>()
      }
    }
  }

  test("hardware pairing check - feature flag disabled") {
    checkHardwareIsPairedFeatureFlag.setFlagValue(false)
    val propsWithPairing = createProps(requirePairedHardware = Required())

    nfcTransactor.transactResult = Ok(Unit)
    stateMachine.test(propsWithPairing) {
      awaitBody<NfcBodyModel> {
        text.shouldBe("Hold device here behind phone")
        status.shouldBeTypeOf<Searching>()
      }

      val transactCalls = nfcTransactor.transactCalls.awaitItem()
        .shouldBeTypeOf<Parameters>()

      // Verify pairing check is NotRequired even though prop is Required
      transactCalls.requirePairedHardware.shouldBe(RequirePairedHardware.NotRequired)
      onSuccessCalls.awaitItem()

      awaitBody<NfcBodyModel> {
        status.shouldBeTypeOf<Success>()
      }
    }
  }

  test("hardware pairing check - bypasses check if in EEK mode") {
    checkHardwareIsPairedFeatureFlag.setFlagValue(true)
    val propsWithPairing = createProps(requirePairedHardware = Required())
    accountService.accountState.value = Ok(
      AccountStatus.ActiveAccount(
        FullAccountMock.copy(
          keybox = EekKeyboxMock
        )
      )
    )

    nfcTransactor.transactResult = Ok(Unit)
    stateMachine.test(propsWithPairing) {
      awaitBody<NfcBodyModel> {
        text.shouldBe("Hold device here behind phone")
        status.shouldBeTypeOf<Searching>()
      }

      val transactCalls = nfcTransactor.transactCalls.awaitItem()
        .shouldBeTypeOf<Parameters>()

      // Verify pairing check is NotRequired even though prop is Required
      transactCalls.requirePairedHardware.shouldBe(RequirePairedHardware.NotRequired)
      onSuccessCalls.awaitItem()

      awaitBody<NfcBodyModel> {
        status.shouldBeTypeOf<Success>()
      }
    }
  }

  test("hardware pairing check - recovery in progress - checks against new hardware") {
    checkHardwareIsPairedFeatureFlag.setFlagValue(true)
    val propsWithPairing = createProps(requirePairedHardware = Required(useRecoveryPubKey = true))

    // Set up current account
    accountService.accountState.value = Ok(AccountStatus.ActiveAccount(FullAccountMock))

    // Set up recovery status with new hardware key
    val newHwAuthKey = HwAuthPublicKey(Secp256k1PublicKey("hw-auth-dpub-new"))
    recoveryStatusService.recoveryStatus.value = InitiatedRecovery(
      fullAccountId = FullAccountIdMock,
      appSpendingKey = AppSpendingPublicKeyMock,
      appGlobalAuthKey = AppGlobalAuthPublicKeyMock,
      appRecoveryAuthKey = AppRecoveryAuthPublicKeyMock,
      hardwareSpendingKey = HwSpendingPublicKeyMock,
      hardwareAuthKey = newHwAuthKey,
      appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignatureMock,
      factorToRecover = Hardware,
      serverRecovery = LostHardwareServerRecoveryMock
    )

    nfcTransactor.transactResult = Ok(Unit)
    stateMachine.test(propsWithPairing) {
      awaitBody<NfcBodyModel> {
        text.shouldBe("Hold device here behind phone")
        status.shouldBeTypeOf<Searching>()
      }

      // Verify pairing check is enabled and the recovery's hw pubkey is used
      val transactCalls =
        nfcTransactor.transactCalls.awaitItem()
          .shouldBeTypeOf<Parameters>()
      transactCalls.requirePairedHardware.shouldBeTypeOf<RequirePairedHardware.Required>()
        .checkHardwareIsPaired("signature", "challenge".encodeUtf8())

      signatureVerifyCalls.awaitItem()
        .publicKey.shouldBe(newHwAuthKey.pubKey)

      onSuccessCalls.awaitItem()

      awaitBody<NfcBodyModel> {
        status.shouldBeTypeOf<Success>()
      }
    }
  }

  test("shows no NFC message when NFC is not available") {
    nfcReaderCapability.availability = NfcAvailability.NotAvailable

    stateMachine.test(props) {
      awaitBody<FormBodyModel>(NfcEventTrackerScreenId.NFC_NOT_AVAILABLE)
    }
  }

  test("shows enable NFC instructions when NFC is disabled") {
    nfcReaderCapability.availability = NfcAvailability.Available.Disabled

    stateMachine.test(props) {
      awaitBody<FormBodyModel>(NfcEventTrackerScreenId.NFC_ENABLE_INSTRUCTIONS)
    }
  }

  test("calls onCancel when back is pressed on no NFC message") {
    nfcReaderCapability.availability = NfcAvailability.NotAvailable

    stateMachine.test(props) {
      awaitBody<FormBodyModel>(NfcEventTrackerScreenId.NFC_NOT_AVAILABLE) {
        onBack?.invoke()
      }

      onCancelCalls.awaitItem()
    }
  }

  test("calls onCancel when back is pressed on enable NFC instructions") {
    nfcReaderCapability.availability = NfcAvailability.Available.Disabled

    stateMachine.test(props) {
      awaitBody<FormBodyModel>(NfcEventTrackerScreenId.NFC_ENABLE_INSTRUCTIONS) {
        onBack?.invoke()
      }

      onCancelCalls.awaitItem()
    }
  }
})
