package bitkey.ui.screens.device

import bitkey.account.AccountConfigServiceFake
import bitkey.privilegedactions.FingerprintResetAvailabilityServiceImpl
import bitkey.ui.framework.test
import build.wallet.availability.AppFunctionalityServiceFake
import build.wallet.availability.AppFunctionalityStatus
import build.wallet.availability.F8eUnreachable
import build.wallet.bitkey.auth.AppGlobalAuthPublicKeyMock2
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.compose.collections.immutableListOf
import build.wallet.coroutines.turbine.awaitUntil
import build.wallet.coroutines.turbine.turbines
import build.wallet.db.DbError
import build.wallet.encrypt.Secp256k1PublicKey
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.FeatureFlagValue
import build.wallet.feature.flags.FingerprintResetMinFirmwareVersionFeatureFlag
import build.wallet.feature.flags.W3OnboardingFeatureFlag
import build.wallet.feature.setFlagValue
import build.wallet.firmware.FirmwareDeviceInfoDaoMock
import build.wallet.firmware.FirmwareDeviceInfoMock
import build.wallet.fwup.*
import build.wallet.fwup.FirmwareData.FirmwareUpdateState.PendingUpdate
import build.wallet.nfc.NfcCommandsMock
import build.wallet.nfc.NfcException
import build.wallet.nfc.NfcSessionFake
import build.wallet.recovery.RecoveryStatusServiceMock
import build.wallet.router.Route
import build.wallet.router.Router
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.form.FormMainContentModel.DataList.Data
import build.wallet.statemachine.core.form.FormMainContentModel.DeviceStatusCard
import build.wallet.statemachine.core.form.FormMainContentModel.SettingsList
import build.wallet.statemachine.fwup.FwupScreen
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineFake
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps.HardwareVerification.NotRequired
import build.wallet.statemachine.settings.full.device.DeviceSettingsFormBodyModel
import build.wallet.statemachine.settings.full.device.fingerprints.ManagingFingerprintsScreen
import build.wallet.statemachine.settings.full.device.fingerprints.fingerprintreset.FingerprintResetProps
import build.wallet.statemachine.settings.full.device.fingerprints.fingerprintreset.FingerprintResetUiStateMachine
import build.wallet.statemachine.settings.full.device.wipedevice.WipingDeviceInitialStep
import build.wallet.statemachine.settings.full.device.wipedevice.WipingDeviceProps
import build.wallet.statemachine.settings.full.device.wipedevice.WipingDeviceUiStateMachine
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.statemachine.ui.awaitUntilBodyMock
import build.wallet.statemachine.walletmigration.W3UpgradeUiProps
import build.wallet.statemachine.walletmigration.W3UpgradeUiStateMachine
import build.wallet.time.ClockFake
import build.wallet.time.DateTimeFormatterMock
import build.wallet.time.DurationFormatterFake
import build.wallet.time.TimeZoneProviderMock
import build.wallet.ui.model.icon.IconImage
import build.wallet.ui.model.list.ListItemTreatment
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory
import build.wallet.ui.tokens.market.MarketIcons
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.get
import io.kotest.core.spec.style.FunSpec
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeTypeOf
import kotlinx.datetime.Instant
import okio.ByteString.Companion.encodeUtf8

class DeviceSettingsScreenPresenterTests : FunSpec({

  val firmwareDeviceInfoDao = FirmwareDeviceInfoDaoMock(turbines::create)
  val appFunctionalityService = AppFunctionalityServiceFake()
  val firmwareDataService = FirmwareDataServiceFake()
  val clock = ClockFake()
  val recoveryStatusService = RecoveryStatusServiceMock(turbine = turbines::create)
  val accountConfigService = AccountConfigServiceFake()

  val featureFlagDao = FeatureFlagDaoFake()
  val fingerprintResetMinFirmwareVersionFeatureFlag = FingerprintResetMinFirmwareVersionFeatureFlag(featureFlagDao)
  val w3OnboardingFeatureFlag = W3OnboardingFeatureFlag(featureFlagDao)

  val fingerprintResetAvailability = FingerprintResetAvailabilityServiceImpl(
    fingerprintResetMinFirmwareVersionFeatureFlag = fingerprintResetMinFirmwareVersionFeatureFlag,
    firmwareDataService = firmwareDataService
  )

  val nfcCommandsMock = NfcCommandsMock(turbines::create)

  val presenter = DeviceSettingsScreenPresenter(
    nfcSessionUIStateMachine = NfcSessionUIStateMachineFake(
      nfcCommands = nfcCommandsMock
    ),
    firmwareDeviceInfoDao = firmwareDeviceInfoDao,
    dateTimeFormatter = DateTimeFormatterMock(),
    timeZoneProvider = TimeZoneProviderMock(),
    durationFormatter = DurationFormatterFake(),
    appFunctionalityService = appFunctionalityService,
    wipingDeviceUiStateMachine = object : WipingDeviceUiStateMachine,
      ScreenStateMachineMock<WipingDeviceProps>("wiping-device") {},
    firmwareDataService = firmwareDataService,
    fingerprintResetUiStateMachine = object : FingerprintResetUiStateMachine,
      ScreenStateMachineMock<FingerprintResetProps>("fingerprint-reset") {},
    fingerprintResetAvailabilityService = fingerprintResetAvailability,
    recoveryStatusService = recoveryStatusService,
    clock = clock,
    w3UpgradeUiStateMachine = object : W3UpgradeUiStateMachine,
      ScreenStateMachineMock<W3UpgradeUiProps>("w3-upgrade") {},
    accountConfigService = accountConfigService,
    w3OnboardingFeatureFlag = w3OnboardingFeatureFlag
  )

  val screen = DeviceSettingsScreen(
    account = FullAccountMock,
    originScreen = null
  )

  beforeTest {
    appFunctionalityService.reset()
    firmwareDeviceInfoDao.reset()
    firmwareDataService.reset()
    recoveryStatusService.reset()
    accountConfigService.reset()
    clock.reset()
    featureFlagDao.reset()
    nfcCommandsMock.reset()
    Router.reset()
  }

  test("metadata is appropriately formatted with update") {
    firmwareDataService.firmwareData.value =
      FirmwareDataUpToDateMock.copy(
        firmwareUpdateState = PendingUpdate(immutableListOf(McuFwupDataMock_W1_CORE))
      )

    presenter.test(screen) { navigator ->
      awaitBody<FormBodyModel> {
        mainContentList[0].apply {
          shouldBeInstanceOf<DeviceStatusCard>()
          statusCallout.shouldNotBeNull().apply {
            title.shouldBe("Update available")
            subtitle.shouldNotBeNull().string.shouldBe(McuFwupDataMock_W1_CORE.version)
          }
        }
      }
    }
  }

  test("metadata is appropriately formatted with no update") {
    presenter.test(screen) { navigator ->
      awaitBody<FormBodyModel> {
        mainContentList[0].apply {
          shouldBeInstanceOf<DeviceStatusCard>()
          statusCallout.shouldNotBeNull().apply {
            title.shouldBe("Last synced")
            subtitle.shouldNotBeNull().string.shouldBe("date-time")
          }
        }
      }
    }
  }

  test("sync device info") {
    firmwareDeviceInfoDao.getDeviceInfo().get().shouldBeNull()
    presenter.test(screen) { navigator ->
      // Device settings - tap on the status card to sync
      awaitBody<FormBodyModel> {
        mainContentList[0].apply {
          shouldBeInstanceOf<DeviceStatusCard>()
          statusCallout.shouldNotBeNull().onClick.shouldNotBeNull().invoke()
        }
      }

      // Syncing info via NFC
      awaitUntilBodyMock<NfcSessionUIStateMachineProps<Result<Unit, DbError>>> {
        hardwareVerification.shouldBe(NotRequired)

        // Verify getDeviceInfo was called
        nfcCommandsMock.getDeviceInfoCalls.awaitItem().shouldBe(FirmwareDeviceInfoMock)
        // W1 devices require auth key lookup to verify the tapped device matches the paired device
        nfcCommandsMock.getAuthenticationKeyCalls.awaitItem()

        // Verify the device info was stored
        firmwareDeviceInfoDao.getDeviceInfo().get().shouldNotBeNull()
      }

      // Back to device settings
      awaitBody<FormBodyModel>()
    }
  }

  test("sync device info from about sheet") {
    presenter.test(screen) { navigator ->
      awaitBody<FormBodyModel> {
        mainContentList[1]
          .shouldBeInstanceOf<SettingsList>()
          .itemWithTitle("About")
          .onClick
          .shouldNotBeNull()
          .invoke()
      }

      awaitUntil { it.bottomSheetModel != null }
        .bottomSheetModel.shouldNotBeNull()
        .body.shouldBeInstanceOf<FormBodyModel>()
        .secondaryButton.shouldNotBeNull()
        .onClick()

      awaitUntilBodyMock<NfcSessionUIStateMachineProps<Result<Unit, DbError>>> {
        hardwareVerification.shouldBe(NotRequired)

        nfcCommandsMock.getDeviceInfoCalls.awaitItem().shouldBe(FirmwareDeviceInfoMock)
        nfcCommandsMock.getAuthenticationKeyCalls.awaitItem()
      }

      awaitItem().bottomSheetModel.shouldNotBeNull()
    }
  }

  test("metadata sync rejects W3 mismatch before auth key lookup") {
    val expectedPairedDeviceInfo = FirmwareDeviceInfoMock.copy(
      hwRevision = "w1a-dvt",
      serial = "paired-w1"
    )
    nfcCommandsMock.deviceInfoResult = FirmwareDeviceInfoMock.copy(
      hwRevision = "w3a-core-evt",
      serial = "unpaired-w3"
    )

    shouldThrow<NfcException.UnpairedHardwareError> {
      verifyTappedDeviceInfoForMetadataSync(
        expectedPairedDeviceInfo = expectedPairedDeviceInfo,
        expectedHwAuthKey = FullAccountMock.keybox.activeHwKeyBundle.authKey,
        session = NfcSessionFake(),
        commands = nfcCommandsMock
      )
    }

    nfcCommandsMock.getDeviceInfoCalls.awaitItem().shouldBe(
      FirmwareDeviceInfoMock.copy(
        hwRevision = "w3a-core-evt",
        serial = "unpaired-w3"
      )
    )
    nfcCommandsMock.getAuthenticationKeyCalls.expectNoEvents()
  }

  test("metadata sync rejects W3 when no paired device info exists") {
    nfcCommandsMock.deviceInfoResult = FirmwareDeviceInfoMock.copy(
      hwRevision = "w3a-core-evt",
      serial = "candidate-w3"
    )

    shouldThrow<NfcException.UnpairedHardwareError> {
      verifyTappedDeviceInfoForMetadataSync(
        expectedPairedDeviceInfo = null,
        expectedHwAuthKey = FullAccountMock.keybox.activeHwKeyBundle.authKey,
        session = NfcSessionFake(),
        commands = nfcCommandsMock
      )
    }

    nfcCommandsMock.getDeviceInfoCalls.awaitItem().shouldBe(
      FirmwareDeviceInfoMock.copy(
        hwRevision = "w3a-core-evt",
        serial = "candidate-w3"
      )
    )
    nfcCommandsMock.getAuthenticationKeyCalls.expectNoEvents()
  }

  test("metadata sync uses auth key matching for W1 when needed") {
    nfcCommandsMock.deviceInfoResult = FirmwareDeviceInfoMock.copy(
      hwRevision = "w1a-dvt",
      serial = "candidate-w1"
    )
    nfcCommandsMock.authenticationKeyResult =
      HwAuthPublicKey(Secp256k1PublicKey("different-hw-auth-dpub"))

    shouldThrow<NfcException.UnpairedHardwareError> {
      verifyTappedDeviceInfoForMetadataSync(
        expectedPairedDeviceInfo = null,
        expectedHwAuthKey = FullAccountMock.keybox.activeHwKeyBundle.authKey,
        session = NfcSessionFake(),
        commands = nfcCommandsMock
      )
    }

    nfcCommandsMock.getDeviceInfoCalls.awaitItem().shouldBe(
      FirmwareDeviceInfoMock.copy(
        hwRevision = "w1a-dvt",
        serial = "candidate-w1"
      )
    )
    nfcCommandsMock.getAuthenticationKeyCalls.awaitItem().shouldBe(Unit)
  }

  test("metadata sync accepts W3 when serial matches paired device") {
    val pairedW3DeviceInfo = FirmwareDeviceInfoMock.copy(
      hwRevision = "w3a-core-evt",
      serial = "paired-w3-serial"
    )
    nfcCommandsMock.deviceInfoResult = pairedW3DeviceInfo

    val result = verifyTappedDeviceInfoForMetadataSync(
      expectedPairedDeviceInfo = pairedW3DeviceInfo,
      expectedHwAuthKey = FullAccountMock.keybox.activeHwKeyBundle.authKey,
      session = NfcSessionFake(),
      commands = nfcCommandsMock
    )

    result.shouldBe(pairedW3DeviceInfo)
    nfcCommandsMock.getDeviceInfoCalls.awaitItem().shouldBe(pairedW3DeviceInfo)
    nfcCommandsMock.getAuthenticationKeyCalls.expectNoEvents()
  }

  test("metadata sync accepts W1 when auth key matches") {
    val pairedW1DeviceInfo = FirmwareDeviceInfoMock.copy(
      hwRevision = "w1a-dvt",
      serial = "paired-w1-serial"
    )
    nfcCommandsMock.deviceInfoResult = pairedW1DeviceInfo
    // authenticationKeyResult defaults to HwAuthSecp256k1PublicKeyMock which matches FullAccountMock

    val result = verifyTappedDeviceInfoForMetadataSync(
      expectedPairedDeviceInfo = pairedW1DeviceInfo,
      expectedHwAuthKey = FullAccountMock.keybox.activeHwKeyBundle.authKey,
      session = NfcSessionFake(),
      commands = nfcCommandsMock
    )

    result.shouldBe(pairedW1DeviceInfo)
    nfcCommandsMock.getDeviceInfoCalls.awaitItem().shouldBe(pairedW1DeviceInfo)
    nfcCommandsMock.getAuthenticationKeyCalls.awaitItem().shouldBe(Unit)
  }

  test("lost or stolen device") {
    w3OnboardingFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
    val routeCalls = turbines.create<Route>("router routes")
    Router.onRouteChange { route ->
      routeCalls.add(route)
      route == Route.InitiateHardwareRecovery
    }

    presenter.test(screen) { navigator ->
      awaitBody<FormBodyModel> {
        mainContentList[1].apply {
          shouldBeInstanceOf<SettingsList>()
            .itemWithTitle("Replace device")
            .onClick
            .shouldNotBeNull()
            .invoke()
        }
      }

      // Note: In the new pattern, lost hardware recovery would trigger a navigation event
      // For now, this is handled via Router.route rather than Navigator
      awaitUntilBody<FormBodyModel>()
      routeCalls.awaitUntil(Route.InitiateHardwareRecovery)
    }
  }

  test("unhandled lost hardware recovery route restores device settings") {
    w3OnboardingFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))

    presenter.test(screen) { navigator ->
      awaitBody<DeviceSettingsFormBodyModel> {
        onReplaceDevice()
      }

      val restoredBody = awaitUntilBody<DeviceSettingsFormBodyModel>(
        matching = { Router.route == Route.InitiateHardwareRecovery && it.showRealtimeMedia }
      )
      restoredBody.onBack()

      awaitUntilBody<FormBodyModel>()
      navigator.exitCalls.awaitItem().shouldBe(Unit)
    }
  }

  test("onBack calls") {
    presenter.test(screen) { navigator ->
      awaitBody<FormBodyModel> {
        val icon =
          toolbar.shouldNotBeNull()
            .leadingAccessory
            .shouldBeInstanceOf<IconAccessory>()

        icon.model.onClick.shouldNotBeNull()
          .invoke()
      }

      awaitUntilBody<FormBodyModel>()
      navigator.exitCalls.awaitItem().shouldBe(Unit)
    }
  }

  test("device settings configures collapsible toolbar for design system screens") {
    presenter.test(screen) { _ ->
      awaitBody<FormBodyModel> {
        designSystemV2Model.shouldNotBeNull().apply {
          title.shouldBe("Bitkey Device")
          toolbar.shouldNotBeNull().apply {
            middleAccessory.shouldBeNull()
            leadingAccessory.shouldBeInstanceOf<IconAccessory>()
          }
        }
      }
    }
  }

  test("device settings orders replace before destructive wipe") {
    presenter.test(screen) { _ ->
      awaitBody<FormBodyModel> {
        val items = mainContentList[1].shouldBeInstanceOf<SettingsList>().items

        items.map { it.title }.shouldBe(
          listOf("About", "Fingerprints", "Replace device", "Upgrade device", "Wipe device")
        )

        items[2].apply {
          icon.iconImage.shouldBe(IconImage.MarketIconImage(MarketIcons.BitkeyWallet))
          treatment.shouldBe(ListItemTreatment.PRIMARY)
        }

        items[4].apply {
          icon.iconImage.shouldBe(IconImage.LocalImage(Icon.SmallIconBitkeyReset))
          treatment.shouldBe(ListItemTreatment.DESTRUCTIVE)
        }
      }
    }
  }

  test("fwup") {
    val version = "fake-version"
    firmwareDataService.firmwareData.value = FirmwareDataPendingUpdateMock.copy(
      firmwareUpdateState =
        PendingUpdate(
          mcuUpdates = immutableListOf(McuFwupDataMock_W1_CORE.copy(version = version))
        )
    )
    presenter.test(screen) { navigator ->
      // Device settings - with firmware update available, status card shows update info
      awaitBody<FormBodyModel> {
        mainContentList[0].apply {
          shouldBeInstanceOf<DeviceStatusCard>()
          statusCallout.shouldNotBeNull().apply {
            title.shouldBe("Update available")
            subtitle.shouldNotBeNull().string.shouldBe(version)
            onClick.shouldNotBeNull().invoke()
          }
        }
      }

      awaitUntilBody<FormBodyModel>()

      // Going to firmware update screen
      val fwupScreen = navigator.goToCalls.awaitUntil<FwupScreen>()
      fwupScreen.onExit.shouldNotBeNull().invoke()

      // Back to device settings
      val deviceSettingsScreen = navigator.goToCalls.awaitItem().shouldBeTypeOf<DeviceSettingsScreen>()
      deviceSettingsScreen.account.shouldBe(screen.account)
    }
  }

  test("Replace device button should be disabled given limited functionality") {
    w3OnboardingFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
    presenter.test(screen) { navigator ->
      awaitBody<FormBodyModel> {
        mainContentList[1]
          .shouldBeInstanceOf<SettingsList>()
          .itemWithTitle("Replace device")
          .isEnabled
          .shouldBeTrue()
      }

      appFunctionalityService.status.emit(
        AppFunctionalityStatus.LimitedFunctionality(
          cause = F8eUnreachable(Instant.DISTANT_PAST)
        )
      )

      awaitBody<FormBodyModel> {
        mainContentList[1]
          .shouldBeInstanceOf<SettingsList>()
          .itemWithTitle("Replace device")
          .isEnabled
          .shouldBeFalse()
      }
    }
  }

  test("replace device limited functionality alert returns to device settings") {
    appFunctionalityService.status.emit(
      AppFunctionalityStatus.LimitedFunctionality(
        cause = F8eUnreachable(Instant.DISTANT_PAST)
      )
    )

    presenter.test(screen) { navigator ->
      awaitBody<DeviceSettingsFormBodyModel> {
        onReplaceDevice()
      }

      awaitUntil { it.alertModel != null }
        .body
        .shouldBeInstanceOf<DeviceSettingsFormBodyModel>()
    }
  }

  test("tap on manage fingerprints") {
    presenter.test(screen) { navigator ->
      // Tap the Fingerprint button (index 1 in SettingsList)
      awaitBody<FormBodyModel> {
        mainContentList[1].apply {
          shouldBeInstanceOf<SettingsList>()
          items[1].onClick!!()
        }
      }

      // Expect the options sheet
      awaitItem().bottomSheetModel.shouldNotBeNull()
        .body.shouldBeInstanceOf<FormBodyModel>().apply {
          primaryButton.shouldNotBeNull().onClick()
        }

      // Going to manage fingerprints
      awaitUntilBody<FormBodyModel>()
      navigator.goToCalls.awaitUntil<ManagingFingerprintsScreen>()
    }
  }

  test("fingerprints row is hidden for W3 hardware") {
    val serialNumber = "350FS20304400455"
    firmwareDataService.firmwareData.value = FirmwareDataUpToDateMock.copy(
      firmwareDeviceInfo = FirmwareDeviceInfoMock.copy(
        hwRevision = "w3a-core-evt",
        version = "1.0.98",
        serial = serialNumber
      )
    )

    presenter.test(screen) { _ ->
      awaitBody<FormBodyModel> {
        val settingsItems = mainContentList[1].shouldBeInstanceOf<SettingsList>().items
        settingsItems.none { it.title == "Fingerprints" }.shouldBe(true)
      }
    }
  }

  test("fingerprints row is hidden when activeHardwareType is W3 even if firmware metadata is stale") {
    // Simulate the window immediately after a W3 upgrade where accountConfig is already W3
    // but firmwareDeviceInfo still has a W1 hwRevision.
    accountConfigService.setActiveConfig(
      bitkey.account.FullAccountConfig(
        bitcoinNetworkType = build.wallet.bitcoin.BitcoinNetworkType.BITCOIN,
        f8eEnvironment = build.wallet.f8e.F8eEnvironment.Production,
        isTestAccount = false,
        isUsingSocRecFakes = false,
        isHardwareFake = false,
        hardwareType = bitkey.account.HardwareType.W3
      )
    )
    // Firmware info still shows W1 revision
    firmwareDataService.firmwareData.value = FirmwareDataUpToDateMock.copy(
      firmwareDeviceInfo = FirmwareDeviceInfoMock.copy(hwRevision = "evta")
    )

    presenter.test(screen) { _ ->
      awaitBody<FormBodyModel> {
        val settingsItems = mainContentList[1].shouldBeInstanceOf<SettingsList>().items
        settingsItems.none { it.title == "Fingerprints" }.shouldBe(true)
      }
    }
  }

  test("tap on manage fingerprints but need fwup") {
    firmwareDataService.firmwareData.value = FirmwareDataPendingUpdateMock

    presenter.test(screen) { navigator ->
      // Tap the Fingerprint button (index 1 in SettingsList)
      awaitBody<FormBodyModel> {
        mainContentList[1].apply {
          shouldBeInstanceOf<SettingsList>()
          items[1].onClick!!()
        }
      }

      // Expect the options sheet
      awaitItem().bottomSheetModel.shouldNotBeNull()
        .body.shouldBeInstanceOf<FormBodyModel>().apply {
          primaryButton.shouldNotBeNull().onClick()
        }

      // Going to manage fingerprints
      awaitUntilBody<FormBodyModel>()
      val managingScreen = navigator.goToCalls.awaitUntil<ManagingFingerprintsScreen>()
      managingScreen.onFwUpRequired()

      // Device settings screen should be showing with a bottom sheet modal
      with(awaitItem()) {
        bottomSheetModel.shouldNotBeNull()
          .body.shouldBeInstanceOf<FormBodyModel>().apply {
            header.shouldNotBeNull()
              .headline.shouldBe("Update your hardware device")

            secondaryButton.shouldNotBeNull().apply {
              text.shouldBe("Update hardware")
              onClick.invoke()
            }
          }
      }

      // Going to firmware update screen
      awaitUntilBody<FormBodyModel>()
      val fwupScreen = navigator.goToCalls.awaitUntil<FwupScreen>()
      fwupScreen.onExit.shouldNotBeNull().invoke()

      // Back to device settings
      val deviceSettingsScreen =
        navigator.goToCalls.awaitItem().shouldBeTypeOf<DeviceSettingsScreen>()
      deviceSettingsScreen.account.shouldBe(screen.account)
    }
  }

  test("tap on reset device shows scan sheet") {
    presenter.test(screen) { navigator ->
      // Tap the Wipe Device button after the non-destructive options.
      awaitBody<FormBodyModel> {
        mainContentList[1]
          .shouldBeInstanceOf<SettingsList>()
          .itemWithTitle("Wipe device")
          .onClick!!
          .invoke()
      }

      awaitItem().bottomSheetModel.shouldNotBeNull()
        .shouldBeInstanceOf<SheetModel>()
        .body.shouldBeInstanceOf<FormBodyModel>()
        .apply {
          header.shouldNotBeNull().apply {
            headline.shouldBe("Permanently wipe your device")
            sublineModel.shouldNotBeNull().string
              .shouldBe("Start by scanning the device you want to wipe.")
          }
          primaryButton.shouldNotBeNull().text.shouldBe("Scan to continue")
          secondaryButton.shouldNotBeNull().text.shouldBe("Cancel")
        }
    }
  }

  test("tap on reset device scan sheet cancel returns to device settings") {
    presenter.test(screen) { navigator ->
      awaitBody<FormBodyModel> {
        mainContentList[1]
          .shouldBeInstanceOf<SettingsList>()
          .itemWithTitle("Wipe device")
          .onClick!!
          .invoke()
      }

      awaitItem().bottomSheetModel.shouldNotBeNull()
        .body.shouldBeInstanceOf<FormBodyModel>()
        .secondaryButton.shouldNotBeNull()
        .onClick()

      awaitUntil { it.bottomSheetModel == null }
        .body.shouldBeInstanceOf<FormBodyModel>()
    }
  }

  test("tap on reset device scan sheet starts wipe flow at scan step") {
    presenter.test(screen) { navigator ->
      awaitBody<FormBodyModel> {
        mainContentList[1]
          .shouldBeInstanceOf<SettingsList>()
          .itemWithTitle("Wipe device")
          .onClick!!
          .invoke()
      }

      awaitItem().bottomSheetModel.shouldNotBeNull()
        .body.shouldBeInstanceOf<FormBodyModel>()
        .primaryButton.shouldNotBeNull()
        .onClick()

      awaitUntilBodyMock<WipingDeviceProps> {
        initialStep.shouldBe(WipingDeviceInitialStep.ScanDevice)
        fullAccount.shouldBe(screen.account)
        onBack()
      }

      // Back on the device settings screen
      awaitBody<FormBodyModel>()
    }
  }

  test("about sheet shows current firmware version when mcu info is empty") {
    val firmwareVersion = "9.9.9"
    firmwareDataService.firmwareData.value =
      FirmwareDataUpToDateMock.copy(
        firmwareDeviceInfo = FirmwareDeviceInfoMock.copy(
          version = firmwareVersion,
          mcuInfo = emptyList()
        )
      )

    presenter.test(screen) { _ ->
      awaitBody<FormBodyModel> {
        mainContentList[1]
          .shouldBeInstanceOf<SettingsList>()
          .items[0]
          .onClick
          .shouldNotBeNull()
          .invoke()
      }

      awaitItem().bottomSheetModel.shouldNotBeNull()
        .body.shouldBeInstanceOf<FormBodyModel>()
        .mainContentList[0]
        .shouldBeInstanceOf<FormMainContentModel.ListGroup>()
        .listGroupModel
        .items[3]
        .apply {
          title.shouldBe("Firmware version")
          sideText.shouldBe(firmwareVersion)
        }
    }
  }

  test("about sheet shows current firmware version composed from mcu info") {
    firmwareDataService.firmwareData.value =
      FirmwareDataUpToDateMock.copy(
        firmwareDeviceInfo = FirmwareDeviceInfoMock.copy(
          version = "1.2.3",
          mcuInfo = listOf(
            build.wallet.firmware.McuInfo(
              mcuRole = build.wallet.firmware.McuRole.CORE,
              mcuName = build.wallet.firmware.McuName.EFR32,
              firmwareVersion = "1.0.1"
            ),
            build.wallet.firmware.McuInfo(
              mcuRole = build.wallet.firmware.McuRole.UXC,
              mcuName = build.wallet.firmware.McuName.STM32U5,
              firmwareVersion = "2.0.2"
            )
          )
        )
      )

    presenter.test(screen) { _ ->
      awaitBody<FormBodyModel> {
        mainContentList[1]
          .shouldBeInstanceOf<SettingsList>()
          .items[0]
          .onClick
          .shouldNotBeNull()
          .invoke()
      }

      awaitItem().bottomSheetModel.shouldNotBeNull()
        .body.shouldBeInstanceOf<FormBodyModel>()
        .mainContentList[0]
        .shouldBeInstanceOf<FormMainContentModel.ListGroup>()
        .listGroupModel
        .items[3]
        .apply {
          title.shouldBe("Firmware version")
          sideText.shouldBe("1.0.1/2.0.2")
        }
    }
  }

  test("fingerprint reset option shows correctly when version requirements met") {
    val featureFlagDao = FeatureFlagDaoFake()
    val fingerprintResetMinFirmwareVersionFeatureFlag =
      FingerprintResetMinFirmwareVersionFeatureFlag(featureFlagDao)
    val w3OnboardingFeatureFlag = W3OnboardingFeatureFlag(featureFlagDao)

    val fingerprintResetAvailability = FingerprintResetAvailabilityServiceImpl(
      fingerprintResetMinFirmwareVersionFeatureFlag = fingerprintResetMinFirmwareVersionFeatureFlag,
      firmwareDataService = firmwareDataService
    )

    val presenterWithAvailability = DeviceSettingsScreenPresenter(
      nfcSessionUIStateMachine =
        object : NfcSessionUIStateMachine, ScreenStateMachineMock<NfcSessionUIStateMachineProps<*>>(
          "nfc-session"
        ) {},
      firmwareDeviceInfoDao = firmwareDeviceInfoDao,
      dateTimeFormatter = DateTimeFormatterMock(),
      timeZoneProvider = TimeZoneProviderMock(),
      durationFormatter = DurationFormatterFake(),
      appFunctionalityService = appFunctionalityService,
      wipingDeviceUiStateMachine =
        object : WipingDeviceUiStateMachine, ScreenStateMachineMock<WipingDeviceProps>(
          "wiping-device"
        ) {},
      firmwareDataService = firmwareDataService,
      fingerprintResetUiStateMachine =
        object : FingerprintResetUiStateMachine, ScreenStateMachineMock<FingerprintResetProps>(
          "fingerprint-reset"
        ) {},
      fingerprintResetAvailabilityService = fingerprintResetAvailability,
      recoveryStatusService = recoveryStatusService,
      clock = clock,
      w3UpgradeUiStateMachine = object : W3UpgradeUiStateMachine,
        ScreenStateMachineMock<W3UpgradeUiProps>("w3-upgrade") {},
      accountConfigService = accountConfigService,
      w3OnboardingFeatureFlag = w3OnboardingFeatureFlag
    )

    // Set supported firmware version
    fingerprintResetMinFirmwareVersionFeatureFlag.setFlagValue(FeatureFlagValue.StringFlag("1.0.98"))
    firmwareDataService.firmwareData.value = FirmwareData(
      firmwareDeviceInfo = FirmwareDeviceInfoMock.copy(version = "1.0.98"),
      firmwareUpdateState = FirmwareData.FirmwareUpdateState.UpToDate
    )

    presenterWithAvailability.test(screen) { navigator ->
      // Tap the Fingerprint button
      awaitBody<FormBodyModel> {
        mainContentList[1].apply {
          shouldBeInstanceOf<SettingsList>()
            .items[1]
            .onClick
            .shouldNotBeNull()
            .invoke()
        }
      }

      // Expect the options sheet with fingerprint reset enabled
      awaitItem().bottomSheetModel.shouldNotBeNull()
        .body.shouldBeInstanceOf<FormBodyModel>().apply {
          header.shouldNotBeNull()
            .headline.shouldBe("Manage fingerprints")

          // Primary button should be "Edit fingerprints"
          primaryButton.shouldNotBeNull().apply {
            text.shouldBe("Edit fingerprints")
          }

          // Secondary button should be available for fingerprint reset
          secondaryButton.shouldNotBeNull().apply {
            text.shouldBe("I can't unlock my Bitkey")
          }
        }
    }
  }

  test("fingerprint reset option disabled when version requirements not met") {
    val featureFlagDao = FeatureFlagDaoFake()
    val fingerprintResetMinFirmwareVersionFeatureFlag =
      FingerprintResetMinFirmwareVersionFeatureFlag(featureFlagDao)
    val w3OnboardingFeatureFlag = W3OnboardingFeatureFlag(featureFlagDao)

    val fingerprintResetAvailability = FingerprintResetAvailabilityServiceImpl(
      fingerprintResetMinFirmwareVersionFeatureFlag = fingerprintResetMinFirmwareVersionFeatureFlag,
      firmwareDataService = firmwareDataService
    )

    val presenterWithAvailability = DeviceSettingsScreenPresenter(
      nfcSessionUIStateMachine =
        object : NfcSessionUIStateMachine, ScreenStateMachineMock<NfcSessionUIStateMachineProps<*>>(
          "nfc-session"
        ) {},
      firmwareDeviceInfoDao = firmwareDeviceInfoDao,
      dateTimeFormatter = DateTimeFormatterMock(),
      timeZoneProvider = TimeZoneProviderMock(),
      durationFormatter = DurationFormatterFake(),
      appFunctionalityService = appFunctionalityService,
      wipingDeviceUiStateMachine =
        object : WipingDeviceUiStateMachine, ScreenStateMachineMock<WipingDeviceProps>(
          "wiping-device"
        ) {},
      firmwareDataService = firmwareDataService,
      fingerprintResetUiStateMachine =
        object : FingerprintResetUiStateMachine, ScreenStateMachineMock<FingerprintResetProps>(
          "fingerprint-reset"
        ) {},
      fingerprintResetAvailabilityService = fingerprintResetAvailability,
      recoveryStatusService = recoveryStatusService,
      clock = clock,
      w3UpgradeUiStateMachine = object : W3UpgradeUiStateMachine,
        ScreenStateMachineMock<W3UpgradeUiProps>("w3-upgrade") {},
      accountConfigService = accountConfigService,
      w3OnboardingFeatureFlag = w3OnboardingFeatureFlag
    )

    // Set unsupported firmware version
    fingerprintResetMinFirmwareVersionFeatureFlag.setFlagValue(FeatureFlagValue.StringFlag("1.0.98"))
    firmwareDataService.firmwareData.value = FirmwareData(
      firmwareDeviceInfo = FirmwareDeviceInfoMock.copy(version = "1.0.95"),
      firmwareUpdateState = FirmwareData.FirmwareUpdateState.UpToDate
    )

    presenterWithAvailability.test(screen) { navigator ->
      // Tap the Fingerprint button
      awaitBody<FormBodyModel> {
        mainContentList[1].apply {
          shouldBeInstanceOf<SettingsList>()
          items[1].onClick.shouldNotBeNull().invoke()
        }
      }

      // Expect the options sheet with fingerprint reset disabled
      awaitItem().bottomSheetModel.shouldNotBeNull()
        .body.shouldBeInstanceOf<FormBodyModel>().apply {
          header.shouldNotBeNull()
            .headline.shouldBe("Manage fingerprints")

          // Primary button should be "Edit fingerprints"
          primaryButton.shouldNotBeNull().apply {
            text.shouldBe("Edit fingerprints")
          }

          // Secondary button should be null (disabled) when version requirements not met
          secondaryButton.shouldBeNull()
        }
    }
  }

  test("replacement pending shows null when no recovery") {
    recoveryStatusService.reset()

    presenter.test(screen) { navigator ->
      awaitBody<FormBodyModel> {
        mainContentList[0].apply {
          shouldBeInstanceOf<DeviceStatusCard>()
            .statusCallout
            .title
            .shouldBe("Last synced")
        }
      }
    }
  }

  test("replacement pending shows remaining time during InitiatedRecovery delay period") {
    val delayEndTime = clock.now() + kotlin.time.Duration.parse("2h")
    val initiatedRecovery =
      build.wallet.recovery.Recovery.StillRecovering.ServerDependentRecovery.InitiatedRecovery(
        fullAccountId = FullAccountMock.accountId,
        appSpendingKey = FullAccountMock.keybox.activeSpendingKeyset.appKey,
        appGlobalAuthKey = FullAccountMock.keybox.activeAppKeyBundle.authKey,
        appRecoveryAuthKey = FullAccountMock.keybox.activeAppKeyBundle.recoveryAuthKey,
        hardwareSpendingKey = FullAccountMock.keybox.activeSpendingKeyset.hardwareKey,
        hardwareAuthKey = FullAccountMock.keybox.activeHwKeyBundle.authKey,
        appGlobalAuthKeyHwSignature = FullAccountMock.keybox.appGlobalAuthKeyHwSignature,
        factorToRecover = build.wallet.bitkey.factor.PhysicalFactor.Hardware,
        serverRecovery = build.wallet.f8e.recovery.LostHardwareServerRecoveryMock.copy(
          delayStartTime = clock.now(),
          delayEndTime = delayEndTime
        ),
        originalAppGlobalAuthKey = AppGlobalAuthPublicKeyMock2
      )
    recoveryStatusService.recoveryStatus.value = initiatedRecovery

    presenter.test(screen) { navigator ->
      awaitBody<FormBodyModel> {
        mainContentList[0].apply {
          shouldBeInstanceOf<DeviceStatusCard>()
            .statusCallout
            .subtitle
            .shouldNotBeNull()
            .string
            .shouldBe("2h")
        }
      }
    }
  }

  test("replacement pending shows 'Awaiting confirmation' for InitiatedRecovery with zero delay") {
    val initiatedRecovery =
      build.wallet.recovery.Recovery.StillRecovering.ServerDependentRecovery.InitiatedRecovery(
        fullAccountId = FullAccountMock.accountId,
        appSpendingKey = FullAccountMock.keybox.activeSpendingKeyset.appKey,
        appGlobalAuthKey = FullAccountMock.keybox.activeAppKeyBundle.authKey,
        appRecoveryAuthKey = FullAccountMock.keybox.activeAppKeyBundle.recoveryAuthKey,
        hardwareSpendingKey = FullAccountMock.keybox.activeSpendingKeyset.hardwareKey,
        hardwareAuthKey = FullAccountMock.keybox.activeHwKeyBundle.authKey,
        appGlobalAuthKeyHwSignature = FullAccountMock.keybox.appGlobalAuthKeyHwSignature,
        factorToRecover = build.wallet.bitkey.factor.PhysicalFactor.Hardware,
        serverRecovery = build.wallet.f8e.recovery.LostHardwareServerRecoveryMock.copy(
          delayStartTime = clock.now(),
          delayEndTime = clock.now() // Delay period is complete
        ),
        originalAppGlobalAuthKey = AppGlobalAuthPublicKeyMock2
      )
    recoveryStatusService.recoveryStatus.value = initiatedRecovery

    presenter.test(screen) { navigator ->
      awaitBody<FormBodyModel> {
        mainContentList[0].apply {
          shouldBeInstanceOf<DeviceStatusCard>()
            .statusCallout
            .subtitle
            .shouldNotBeNull()
            .string
            .shouldBe("0s")
        }
      }
    }
  }

  test("replacement pending shows 'Awaiting confirmation' for RotatedAuthKeys state") {
    val rotatedAuthKeys =
      build.wallet.recovery.Recovery.StillRecovering.ServerIndependentRecovery.RotatedAuthKeys(
        fullAccountId = FullAccountMock.accountId,
        appSpendingKey = FullAccountMock.keybox.activeSpendingKeyset.appKey,
        appGlobalAuthKey = FullAccountMock.keybox.activeAppKeyBundle.authKey,
        appRecoveryAuthKey = FullAccountMock.keybox.activeAppKeyBundle.recoveryAuthKey,
        hardwareSpendingKey = FullAccountMock.keybox.activeSpendingKeyset.hardwareKey,
        hardwareAuthKey = FullAccountMock.keybox.activeHwKeyBundle.authKey,
        appGlobalAuthKeyHwSignature = FullAccountMock.keybox.appGlobalAuthKeyHwSignature,
        factorToRecover = build.wallet.bitkey.factor.PhysicalFactor.Hardware,
        sealedCsek = "sealed-csek".encodeUtf8(),
        sealedSsek = null,
        originalAppGlobalAuthKey = null
      )
    recoveryStatusService.recoveryStatus.value = rotatedAuthKeys

    presenter.test(screen) { navigator ->
      awaitBody<FormBodyModel> {
        mainContentList[0].apply {
          shouldBeInstanceOf<DeviceStatusCard>()
            .statusCallout
            .subtitle
            .shouldNotBeNull()
            .string
            .shouldBe("Awaiting confirmation")
        }
      }
    }
  }

  test("replacement pending shows 'Awaiting confirmation' for CreatedSpendingKeys state") {
    val createdSpendingKeys =
      build.wallet.recovery.Recovery.StillRecovering.ServerIndependentRecovery.CreatedSpendingKeys(
        fullAccountId = FullAccountMock.accountId,
        appSpendingKey = FullAccountMock.keybox.activeSpendingKeyset.appKey,
        appGlobalAuthKey = FullAccountMock.keybox.activeAppKeyBundle.authKey,
        appRecoveryAuthKey = FullAccountMock.keybox.activeAppKeyBundle.recoveryAuthKey,
        hardwareSpendingKey = FullAccountMock.keybox.activeSpendingKeyset.hardwareKey,
        hardwareAuthKey = FullAccountMock.keybox.activeHwKeyBundle.authKey,
        appGlobalAuthKeyHwSignature = FullAccountMock.keybox.appGlobalAuthKeyHwSignature,
        factorToRecover = build.wallet.bitkey.factor.PhysicalFactor.Hardware,
        sealedCsek = "sealed-csek".encodeUtf8(),
        sealedSsek = null,
        f8eSpendingKeyset = FullAccountMock.keybox.activeSpendingKeyset.f8eSpendingKeyset,
        originalAppGlobalAuthKey = null
      )
    recoveryStatusService.recoveryStatus.value = createdSpendingKeys

    presenter.test(screen) { navigator ->
      awaitBody<FormBodyModel> {
        mainContentList[0].apply {
          shouldBeInstanceOf<DeviceStatusCard>()
            .statusCallout
            .subtitle
            .shouldNotBeNull()
            .string
            .shouldBe("Awaiting confirmation")
        }
      }
    }
  }

  test("W3 upgrade completion navigates to Money Home with post-upgrade origin") {
    w3OnboardingFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
    presenter.test(screen) { navigator ->
      // Tap the Upgrade device button after the replacement row.
      awaitBody<FormBodyModel> {
        mainContentList[1]
          .shouldBeInstanceOf<SettingsList>()
          .itemWithTitle("Upgrade device")
          .onClick
          .shouldNotBeNull()
          .invoke()
      }

      // W3 upgrade state machine is shown - invoke onUpgradeComplete callback
      awaitUntilBodyMock<W3UpgradeUiProps> {
        onUpgradeComplete(FullAccountMock)
      }

      // Verify navigation to Money Home via Router
      Router.route.shouldBe(Route.W3UpgradeComplete)
    }
  }
})

private fun List<Data>.verifyMetadataDataList() {
  forEachIndexed { index, data ->
    when (index) {
      0 -> data.verifyMetadataData("Model name", "Bitkey")
      1 -> data.verifyMetadataData("Model number", "evtd")
      2 -> data.verifyMetadataData("Serial number", "serial")
      3 -> data.verifyMetadataData("Firmware version", "1.2.3")
      4 -> data.verifyMetadataData(
        "Last known charge",
        "100%"
      ) // Not 89% due to battery level masking
      5 -> data.verifyMetadataData(
        "Last sync",
        "date-time"
      )
    }
  }
}

private fun List<Data>.verifyMetadataDataListWithReplacement(replacementStatus: String) {
  forEachIndexed { index, data ->
    when (index) {
      0 -> data.verifyMetadataData("Model name", "Bitkey")
      1 -> data.verifyMetadataData("Model number", "evtd")
      2 -> data.verifyMetadataData("Serial number", "serial")
      3 -> data.verifyMetadataData("Firmware version", "1.2.3")
      4 -> data.verifyMetadataData(
        "Last known charge",
        "100%"
      ) // Not 89% due to battery level masking
      5 -> data.verifyMetadataData(
        "Last sync",
        "date-time"
      )
      6 -> data.verifyMetadataData("Replacement pending", replacementStatus)
    }
  }
}

private fun Data.verifyMetadataData(
  title: String,
  sideText: String,
) {
  this.title.shouldBe(title)
  this.sideText.shouldBe(sideText)
}

private fun SettingsList.itemWithTitle(title: String): SettingsList.SettingsListItem =
  items.first { it.title == title }
