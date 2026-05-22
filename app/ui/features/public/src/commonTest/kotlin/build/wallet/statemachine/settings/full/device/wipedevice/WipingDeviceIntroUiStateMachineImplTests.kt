package build.wallet.statemachine.settings.full.device.wipedevice

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import bitkey.account.HardwareType.W1
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.balance.BitcoinBalanceFake
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.FullAccountW3Mock
import build.wallet.device.wipe.DeviceWipeEligibility.InactiveHasFunds
import build.wallet.device.wipe.DeviceWipeEligibility.InactiveReady
import build.wallet.device.wipe.DeviceWipeEligibility.ActiveHasFunds
import build.wallet.device.wipe.DeviceWipeEligibility.ActiveReady
import build.wallet.device.wipe.DeviceWipeEligibilityError
import build.wallet.device.wipe.DeviceWipeEligibilityServiceFake
import build.wallet.device.wipe.InactiveHardwareDevice
import build.wallet.coroutines.turbine.turbines
import build.wallet.firmware.EnrolledFingerprints
import build.wallet.firmware.FirmwareDeviceInfoMock
import build.wallet.limit.MobilePayServiceMock
import build.wallet.money.BitcoinMoney
import build.wallet.money.display.FiatCurrencyPreferenceRepositoryMock
import build.wallet.money.exchange.CurrencyConverterFake
import build.wallet.money.formatter.MoneyDisplayFormatterFake
import build.wallet.nfc.NfcCommandsMock
import build.wallet.nfc.NfcException
import build.wallet.nfc.NfcSession
import build.wallet.nfc.NfcSessionFake
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.settings.full.device.wipedevice.intro.WipingDeviceIntroProps
import build.wallet.statemachine.settings.full.device.wipedevice.intro.WipingDeviceIntroUiStateMachineImpl
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitUntilBodyMock
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class WipingDeviceIntroUiStateMachineImplTests : FunSpec({
  val mobilePayService = MobilePayServiceMock(turbines::create)
  val deviceWipeEligibilityService = DeviceWipeEligibilityServiceFake()

  val stateMachine = WipingDeviceIntroUiStateMachineImpl(
    nfcSessionUIStateMachine =
      object : NfcSessionUIStateMachine, ScreenStateMachineMock<NfcSessionUIStateMachineProps<*>>(
        "nfc-session"
      ) {},
    deviceWipeEligibilityService = deviceWipeEligibilityService,
    moneyDisplayFormatter = MoneyDisplayFormatterFake,
    fiatCurrencyPreferenceRepository = FiatCurrencyPreferenceRepositoryMock(turbines::create),
    currencyConverter = CurrencyConverterFake(conversionRate = 3.0),
    mobilePayService = mobilePayService
  )

  val onBackCalls = turbines.create<Unit>("on back calls")
  val onDeviceConfirmedCalls = turbines.create<Pair<Boolean, WipeContext>>("on device confirmed calls")

  val props = WipingDeviceIntroProps(
    onBack = { onBackCalls += Unit },
    onUnwindToMoneyHome = {},
    onDeviceConfirmed = { paired, wipeContext -> onDeviceConfirmedCalls += paired to wipeContext },
    fullAccount = FullAccountMock
  )

  fun nfcCommandsMock(id: String) = NfcCommandsMock { name ->
    Turbine(name = "$id $name")
  }

  beforeTest {
    deviceWipeEligibilityService.reset()
  }

  suspend fun assertServiceErrorShows(
    error: DeviceWipeEligibilityError,
    headline: String,
    subline: String? = null,
  ) {
    deviceWipeEligibilityService.evaluateLoggedInDeviceResult = Err(error)

    stateMachine.test(props.copy(fullAccount = FullAccountW3Mock)) {
      startScan()

      awaitNfcProps().apply {
        onSuccess(session(NfcSessionFake(), nfcCommandsMock("service-error")))
      }

      awaitCheckingDevice()

      awaitBody<FormBodyModel> {
        header.shouldNotBeNull().apply {
          this.headline.shouldBe(headline)
          subline?.let { this.sublineModel.shouldNotBeNull().string.shouldBe(it) }
        }
      }
    }
  }

  test("onBack calls") {
    stateMachine.test(props) {
      awaitBody<FormBodyModel> {
        val icon = toolbar.shouldNotBeNull()
          .leadingAccessory
          .shouldBeInstanceOf<ToolbarAccessoryModel.IconAccessory>()

        icon.model.onClick.shouldNotBeNull()
          .invoke()
      }

      onBackCalls.awaitItem().shouldBe(Unit)
    }
  }

  test("tap to confirm sheet can be shown and dismissed") {
    stateMachine.test(props) {
      awaitBody<FormBodyModel> {
        primaryButton.shouldNotBeNull()

        primaryButton.shouldBeInstanceOf<ButtonModel>().apply {
          text.shouldBe("Wipe device")
          treatment.shouldBe(ButtonModel.Treatment.Primary)
          onClick.invoke()
        }
      }

      with(awaitItem()) {
        bottomSheetModel.shouldNotBeNull()
          .shouldBeInstanceOf<SheetModel>()
          .body.shouldBeInstanceOf<FormBodyModel>()
          .secondaryButton?.onClick?.invoke()
      }

      awaitBody<FormBodyModel>()
    }
  }

  test("ScanDevice initial step immediately emits NFC props") {
    stateMachine.test(props.copy(initialStep = WipingDeviceInitialStep.ScanDevice)) {
      awaitNfcProps().apply {
        showNativeSheetOnIos.shouldBeFalse()
        skipFirmwareTelemetry.shouldBe(true)
        needsAuthentication.shouldBe(false)
        eventTrackerContext.shouldBe(NfcEventTrackerScreenIdContext.WIPE_DEVICE_CLASSIFY_DEVICE)
      }
    }
  }

  test("old-device scan blocks the active paired device") {
    deviceWipeEligibilityService.evaluateLoggedInDeviceResult = Ok(ActiveReady)

    stateMachine.test(
      props.copy(
        fullAccount = FullAccountW3Mock,
        initialStep = WipingDeviceInitialStep.ScanDevice,
        wipeContext = WipeContext.InactiveDevice(inactiveW1Device("old-fingerprint"))
      )
    ) {
      awaitNfcProps().apply {
        onSuccess(session(NfcSessionFake(), nfcCommandsMock("active-device-for-old-wipe")))
      }

      awaitCheckingDevice()

      awaitBody<FormBodyModel> {
        header.shouldNotBeNull().apply {
          headline.shouldBe("This is your active Bitkey")
          sublineModel.shouldNotBeNull().string
            .shouldBe("Scan the first generation Bitkey you replaced during upgrade to wipe it.")
        }
      }
    }
  }

  test("old-device scan matching historical-W1-ready result preserves reminder wipe context") {
    deviceWipeEligibilityService.evaluateLoggedInDeviceResult = Ok(
      InactiveReady(
        inactiveW1Device("reminder-old-fingerprint")
      )
    )

    stateMachine.test(
      props.copy(
        fullAccount = FullAccountW3Mock,
        initialStep = WipingDeviceInitialStep.ScanDevice,
        wipeContext = WipeContext.InactiveDevice(inactiveW1Device("reminder-old-fingerprint"))
      )
    ) {
      awaitNfcProps().apply {
        onSuccess(session(NfcSessionFake(), nfcCommandsMock("old-device-for-reminder")))
      }

      awaitCheckingDevice()

      onDeviceConfirmedCalls.awaitItem().shouldBe(
        false to WipeContext.InactiveDevice(inactiveW1Device("reminder-old-fingerprint"))
      )
    }
  }

  test("old-device scan mismatched historical-W1-ready result shows unknown-device screen") {
    deviceWipeEligibilityService.evaluateLoggedInDeviceResult = Ok(
      InactiveReady(
        inactiveW1Device("different-old-fingerprint")
      )
    )

    stateMachine.test(
      props.copy(
        fullAccount = FullAccountW3Mock,
        initialStep = WipingDeviceInitialStep.ScanDevice,
        wipeContext = WipeContext.InactiveDevice(inactiveW1Device("reminder-old-fingerprint"))
      )
    ) {
      awaitNfcProps().apply {
        onSuccess(session(NfcSessionFake(), nfcCommandsMock("wrong-old-device-for-reminder")))
      }

      awaitCheckingDevice()

      awaitBody<FormBodyModel> {
        header.shouldNotBeNull().apply {
          headline.shouldBe("This Bitkey can’t be wiped from this wallet")
          sublineModel.shouldNotBeNull().string
            .shouldBe(
              "The device you tapped isn’t paired with this wallet and doesn’t match a Bitkey previously used by this account."
            )
        }
      }
      onDeviceConfirmedCalls.expectNoEvents()
    }
  }

  test("service paired-device-ready result shows active-device info before paired wipe") {
    deviceWipeEligibilityService.evaluateLoggedInDeviceResult = Ok(ActiveReady)

    stateMachine.test(props) {
      startScan()

      awaitNfcProps().apply {
        showNativeSheetOnIos.shouldBeFalse()
        skipFirmwareTelemetry.shouldBe(true)
        needsAuthentication.shouldBe(false)
        eventTrackerContext.shouldBe(NfcEventTrackerScreenIdContext.WIPE_DEVICE_CLASSIFY_DEVICE)
        onSuccess(session(NfcSessionFake(), nfcCommandsMock("paired-ready")))
      }

      awaitCheckingDevice()

      continueFromActiveDeviceInfo()

      onDeviceConfirmedCalls.awaitItem().shouldBe(true to WipeContext.Default)
      deviceWipeEligibilityService.evaluateLoggedInDeviceCalls.single().account
        .shouldBe(FullAccountMock)
    }
  }

  test("W3 paired classification passes tapped auth key to eligibility service") {
    deviceWipeEligibilityService.evaluateLoggedInDeviceResult = Ok(ActiveReady)

    stateMachine.test(props.copy(fullAccount = FullAccountW3Mock)) {
      startScan()

      awaitNfcProps().apply {
        val commands = nfcCommandsMock("paired-w3").apply {
          deviceInfoResult = FirmwareDeviceInfoMock.copy(
            serial = "current-w3",
            hwRevision = "w3a-core-evt"
          )
        }
        onSuccess(session(NfcSessionFake(), commands))
      }

      awaitCheckingDevice()

      continueFromActiveDeviceInfo()

      onDeviceConfirmedCalls.awaitItem().shouldBe(true to WipeContext.Default)
      deviceWipeEligibilityService.evaluateLoggedInDeviceCalls.single().apply {
        account.shouldBe(FullAccountW3Mock)
        tappedDevice.authKey.shouldBe(FullAccountW3Mock.keybox.activeHwKeyBundle.authKey)
        tappedDevice.initialSpendingKeyFingerprint.shouldBe("e5ff120e")
      }
    }
  }

  test("initial old-device scan treats unauthenticated spending key and no fingerprints as already wiped") {
    stateMachine.test(props.copy(fullAccount = FullAccountW3Mock)) {
      startScan()

      awaitNfcProps().apply {
        val error = shouldThrow<NfcException.DeviceAlreadyWipedOrNotSetUp> {
          session(
            NfcSessionFake(),
            nfcCommandsWithInitialSpendingKeyError(
              id = "already-wiped",
              initialSpendingKeyError = NfcException.CommandErrorUnauthenticated()
            )
          )
        }

        onError(error).shouldBe(true)
      }

      awaitBody<FormBodyModel> {
        id.shouldBe(WipingDeviceEventTrackerScreenId.RESET_DEVICE_OLD_DEVICE_ALREADY_WIPED_OR_NOT_SET_UP)
        header.shouldNotBeNull().apply {
          headline.shouldBe("No wipe needed")
          sublineModel.shouldNotBeNull().string.shouldBe(
            "This Bitkey is already wiped or hasn’t been set up."
          )
        }
        primaryButton.shouldNotBeNull().apply {
          text.shouldBe("Done")
          onClick()
        }
        secondaryButton.shouldBe(null)
      }

      onBackCalls.awaitItem().shouldBe(Unit)
      onDeviceConfirmedCalls.expectNoEvents()
      deviceWipeEligibilityService.evaluateLoggedInDeviceCalls.shouldBe(emptyList())
    }
  }

  test("initial old-device scan device locked is not treated as already wiped") {
    stateMachine.test(props.copy(fullAccount = FullAccountW3Mock)) {
      startScan()

      awaitNfcProps().apply {
        val error = shouldThrow<NfcException.CommandErrorUnauthenticated> {
          session(
            NfcSessionFake(),
            nfcCommandsWithInitialSpendingKeyError(
              id = "device-locked",
              initialSpendingKeyError = NfcException.CommandErrorUnauthenticated(),
              enrolledFingerprintsError = NfcException.CommandErrorUnauthenticated()
            )
          )
        }

        onError(error).shouldBe(false)
      }

      onDeviceConfirmedCalls.expectNoEvents()
      deviceWipeEligibilityService.evaluateLoggedInDeviceCalls.shouldBe(emptyList())
    }
  }

  test("service paired-device-has-funds result shows transfer sheet with returned balance") {
    val balance = BitcoinBalanceFake(confirmed = BitcoinMoney.sats(100_000))
    deviceWipeEligibilityService.evaluateLoggedInDeviceResult = Ok(ActiveHasFunds(balance))

    stateMachine.test(props) {
      startScan()

      awaitNfcProps().apply {
        onSuccess(session(NfcSessionFake(), nfcCommandsMock("paired-has-funds")))
      }

      awaitCheckingDevice()

      continueFromActiveDeviceInfo()

      with(awaitItem()) {
        bottomSheetModel.shouldNotBeNull()
          .body.shouldBeInstanceOf<FormBodyModel>()
          .id.shouldBe(WipingDeviceEventTrackerScreenId.RESET_DEVICE_TRANSFER_FUNDS)
      }

      with(awaitItem()) {
        val body = bottomSheetModel.shouldNotBeNull()
          .body.shouldBeInstanceOf<FormBodyModel>()

        body.header.shouldNotBeNull().headline.shouldBe("Transfer funds before you wipe the device")
        val listGroup = body.mainContentList[0].shouldBeInstanceOf<FormMainContentModel.ListGroup>()
        listGroup.listGroupModel.header.shouldBe("Your funds")
        listGroup.listGroupModel.items[0].title.shouldBe("$0.00")
        listGroup.listGroupModel.items[0].secondaryText.shouldBe("100,000 sats")
      }
    }
  }

  test("service historical-W1-ready result directly passes old wipe context") {
    deviceWipeEligibilityService.evaluateLoggedInDeviceResult = Ok(
      InactiveReady(
        inactiveW1Device("old-fingerprint")
      )
    )

    stateMachine.test(props.copy(fullAccount = FullAccountW3Mock)) {
      startScan()

      awaitNfcProps().apply {
        val commands = nfcCommandsMock("old-w1").apply {
          deviceInfoResult = FirmwareDeviceInfoMock.copy(
            serial = "old-serial",
            hwRevision = "w1a-dvt"
          )
        }
        onSuccess(session(NfcSessionFake(), commands))
      }

      awaitCheckingDevice()

      onDeviceConfirmedCalls.awaitItem().shouldBe(
        false to WipeContext.InactiveDevice(inactiveW1Device("old-fingerprint"))
      )
      deviceWipeEligibilityService.evaluateLoggedInDeviceCalls.single()
        .tappedDevice.initialSpendingKeyFingerprint.shouldBe("e5ff120e")
    }
  }

  test("service unknown-device error shows unknown-device screen") {
    assertServiceErrorShows(
      error = DeviceWipeEligibilityError.UnknownDevice,
      headline = "This Bitkey can’t be wiped from this wallet"
    )
  }

  test("service paired-device-balance-check error shows balance check screen") {
    assertServiceErrorShows(
      error = DeviceWipeEligibilityError.PairedDeviceBalanceCheckFailed,
      headline = "We’re having trouble loading your device details"
    )
  }

  test("service old-device pending active transaction error shows pending transfer screen") {
    assertServiceErrorShows(
      error = DeviceWipeEligibilityError.OldDevicePendingActiveTransaction,
      headline = "Your first generation Bitkey device is not ready to wipe",
      subline = "Your sweep transaction is pending. Once it’s confirmed, you’ll be all set to wipe your device."
    )
  }

  test("service old-device pending sweep confirmation error asks user to wait") {
    assertServiceErrorShows(
      error = DeviceWipeEligibilityError.OldDeviceSweepPendingConfirmation,
      headline = "Your first generation Bitkey device is not ready to wipe",
      subline = "Your sweep transaction is pending. Once it’s confirmed, you’ll be all set to wipe your device."
    )
  }

  test("service old-device check failure shows retryable check failed screen") {
    assertServiceErrorShows(
      error = DeviceWipeEligibilityError.OldDeviceCheckFailed,
      headline = "We’re having trouble loading your device details"
    )
  }

  test("service inactive-has-funds outcome shows transfer funds screen") {
    deviceWipeEligibilityService.evaluateLoggedInDeviceResult =
      Ok(InactiveHasFunds(inactiveW1Device("old-fingerprint")))

    stateMachine.test(props.copy(fullAccount = FullAccountW3Mock)) {
      startScan()

      awaitNfcProps().apply {
        onSuccess(session(NfcSessionFake(), nfcCommandsMock("old-device-funds-sweepable")))
      }

      awaitCheckingDevice()

      awaitBody<FormBodyModel> {
        header.shouldNotBeNull().headline.shouldBe("Transfer funds before wiping")
      }
    }
  }

  test("logged-out unpaired wipe flow remains unchanged") {
    stateMachine.test(props.copy(fullAccount = null)) {
      startScan()

      awaitNfcProps().apply {
        hardwareVerification.shouldBe(NfcSessionUIStateMachineProps.HardwareVerification.NotRequired)
        needsAuthentication.shouldBe(false)
        onSuccess(session(NfcSessionFake(), nfcCommandsMock("logged-out-unpaired")))
      }

      with(awaitItem()) {
        bottomSheetModel.shouldNotBeNull()
          .body.shouldBeInstanceOf<FormBodyModel>()
          .apply {
            id.shouldBe(WipingDeviceEventTrackerScreenId.RESET_DEVICE_UNPAIRED_WARNING)
            primaryButton.shouldNotBeNull().onClick()
          }
      }

      onDeviceConfirmedCalls.awaitItem().shouldBe(false to WipeContext.Default)
    }
  }
})

private suspend fun ReceiveTurbine<ScreenModel>.startScan() {
  awaitBody<FormBodyModel> {
    primaryButton.shouldNotBeNull().onClick()
  }

  awaitItem().bottomSheetModel.shouldNotBeNull()
    .body.shouldBeInstanceOf<FormBodyModel>()
    .primaryButton.shouldNotBeNull()
    .onClick()
}

private suspend fun ReceiveTurbine<ScreenModel>.continueFromActiveDeviceInfo() {
  awaitBody<FormBodyModel> {
    header.shouldNotBeNull().apply {
      headline.shouldBe("Permanently wipe your current Bitkey device")
      sublineModel.shouldNotBeNull().string.shouldBe(
        "We noticed you tapped the Bitkey device that is currently paired to your wallet.\n\n" +
          "If you want to wipe a Bitkey that was previously paired to your wallet, go back and try again using your other Bitkey device."
      )
    }
    primaryButton.shouldNotBeNull().apply {
      text.shouldBe("Continue")
      onClick()
    }
  }
}

private suspend fun ReceiveTurbine<ScreenModel>.awaitCheckingDevice() {
  awaitBody<LoadingSuccessBodyModel> {
    id.shouldBe(WipingDeviceEventTrackerScreenId.RESET_DEVICE_CHECKING_ELIGIBILITY)
    message.shouldBe("Checking device")
    state.shouldBe(LoadingSuccessBodyModel.State.Loading)
  }
}

@Suppress("UNCHECKED_CAST")
private suspend fun ReceiveTurbine<ScreenModel>.awaitNfcProps(): NfcSessionUIStateMachineProps<Any?> =
  awaitUntilBodyMock<NfcSessionUIStateMachineProps<*>>(id = "nfc-session") {
  } as NfcSessionUIStateMachineProps<Any?>

private fun nfcCommandsWithInitialSpendingKeyError(
  id: String,
  initialSpendingKeyError: NfcException,
  enrolledFingerprintsError: NfcException? = null,
) = object : NfcCommandsMock({ name ->
    Turbine(name = "$id $name")
  }) {
  override suspend fun getInitialSpendingKey(
    session: NfcSession,
    network: BitcoinNetworkType,
  ): HwSpendingPublicKey {
    throw initialSpendingKeyError
  }

  override suspend fun getEnrolledFingerprints(session: NfcSession): EnrolledFingerprints {
    enrolledFingerprintsError?.let { throw it }
    return super.getEnrolledFingerprints(session)
  }
}

private fun inactiveW1Device(
  hardwareFingerprint: String,
) = InactiveHardwareDevice(
  hardwareType = W1,
  hardwareFingerprint = hardwareFingerprint
)
