package bitkey.ui.screens.securityhub

import bitkey.privilegedactions.FingerprintResetAvailabilityServiceImpl
import bitkey.securitycenter.EekBackupHealthAction
import bitkey.securitycenter.FingerprintsAction
import bitkey.securitycenter.SecurityActionRecommendation.*
import bitkey.securitycenter.SecurityActionType
import bitkey.securitycenter.SecurityActionsServiceFake
import bitkey.securitycenter.SecurityRecommendationWithStatus
import bitkey.ui.framework.test
import bitkey.ui.screens.securityhub.education.SecurityHubEducationScreen
import build.wallet.availability.AppFunctionalityServiceFake
import build.wallet.availability.FunctionalityFeatureStates
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.cloud.backup.health.EekBackupStatus
import build.wallet.compose.collections.immutableListOf
import build.wallet.database.SecurityInteractionStatus
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.FeatureFlagValue
import build.wallet.feature.flags.FingerprintResetFeatureFlag
import build.wallet.feature.flags.FingerprintResetMinFirmwareVersionFeatureFlag
import build.wallet.firmware.EnrolledFingerprints
import build.wallet.firmware.FingerprintHandle
import build.wallet.firmware.FirmwareDeviceInfoMock
import build.wallet.fwup.FirmwareData
import build.wallet.fwup.FirmwareDataPendingUpdateMock
import build.wallet.fwup.FirmwareDataServiceFake
import build.wallet.navigation.v1.NavigationScreenId
import build.wallet.platform.haptics.HapticsMock
import build.wallet.router.Router
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.StateMachineMock
import build.wallet.statemachine.cloud.health.CloudBackupHealthDashboardScreen
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryDataMock
import build.wallet.statemachine.fwup.FwupScreen
import build.wallet.statemachine.moneyhome.card.CardModel
import build.wallet.statemachine.recovery.hardware.HardwareRecoveryStatusCardUiProps
import build.wallet.statemachine.recovery.hardware.HardwareRecoveryStatusCardUiStateMachine
import build.wallet.statemachine.recovery.hardware.fingerprintreset.FingerprintResetStatusCardUiProps
import build.wallet.statemachine.recovery.hardware.fingerprintreset.FingerprintResetStatusCardUiStateMachine
import build.wallet.statemachine.recovery.socrec.RecoveryContactCardsUiProps
import build.wallet.statemachine.recovery.socrec.RecoveryContactCardsUiStateMachine
import build.wallet.statemachine.settings.full.device.fingerprints.ManageFingerprintsOptionsSheet
import build.wallet.statemachine.settings.full.device.fingerprints.ManagingFingerprintsScreen
import build.wallet.statemachine.settings.full.device.fingerprints.fingerprintreset.FingerprintResetProps
import build.wallet.statemachine.settings.full.device.fingerprints.fingerprintreset.FingerprintResetUiStateMachine
import build.wallet.statemachine.status.HomeStatusBannerUiProps
import build.wallet.statemachine.status.HomeStatusBannerUiStateMachine
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitBodyMock
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.time.ClockFake
import build.wallet.ui.model.status.StatusBannerModel
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeTypeOf
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.runBlocking

class SecurityHubPresenterTests : FunSpec({
  val clock = ClockFake()
  val securityActionsService = SecurityActionsServiceFake()
  val haptics = HapticsMock()

  val firmwareDataService = FirmwareDataServiceFake()
  firmwareDataService.pendingUpdate = FirmwareDataPendingUpdateMock

  val featureFlagDao = FeatureFlagDaoFake()

  val fingerprintResetFeatureFlag = FingerprintResetFeatureFlag(
    featureFlagDao = featureFlagDao
  )
  val fingerprintResetMinFirmwareVersionFeatureFlag = FingerprintResetMinFirmwareVersionFeatureFlag(featureFlagDao)

  val fingerprintResetAvailabilityService = FingerprintResetAvailabilityServiceImpl(
    fingerprintResetFeatureFlag = fingerprintResetFeatureFlag,
    fingerprintResetMinFirmwareVersionFeatureFlag = fingerprintResetMinFirmwareVersionFeatureFlag,
    firmwareDataService = firmwareDataService
  )

  val fingerprintResetUiStateMachine = object : FingerprintResetUiStateMachine,
    ScreenStateMachineMock<FingerprintResetProps>(
      id = "reset-fingerprints"
    ) {}

  val presenter = SecurityHubPresenter(
    securityActionsService = securityActionsService,
    homeStatusBannerUiStateMachine = object : HomeStatusBannerUiStateMachine,
      StateMachineMock<HomeStatusBannerUiProps, StatusBannerModel?>(initialModel = null) {},
    firmwareDataService = firmwareDataService,
    recoveryContactCardsUiStateMachine = object : RecoveryContactCardsUiStateMachine,
      StateMachineMock<RecoveryContactCardsUiProps, ImmutableList<CardModel>>(
        initialModel = immutableListOf()
      ) {},
    hardwareRecoveryStatusCardUiStateMachine = object : HardwareRecoveryStatusCardUiStateMachine,
      StateMachineMock<HardwareRecoveryStatusCardUiProps, CardModel?>(
        initialModel = null
      ) {},
    fingerprintResetStatusCardUiStateMachine = object : FingerprintResetStatusCardUiStateMachine,
      StateMachineMock<FingerprintResetStatusCardUiProps, CardModel?>(
        initialModel = null
      ) {},
    fingerprintResetUiStateMachine = fingerprintResetUiStateMachine,
    appFunctionalityService = AppFunctionalityServiceFake(),
    haptics = haptics,
    fingerprintResetAvailabilityService = fingerprintResetAvailabilityService
  )

  suspend fun setupFingerprintResetFeatureFlags(
    featureEnabled: Boolean = true,
    minFirmwareVersion: String = "1.0.98",
  ) {
    fingerprintResetFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(featureEnabled))
    fingerprintResetMinFirmwareVersionFeatureFlag.setFlagValue(FeatureFlagValue.StringFlag(minFirmwareVersion))
  }

  fun setupFirmwareVersion(version: String) {
    firmwareDataService.firmwareData.value = FirmwareData(
      firmwareDeviceInfo = FirmwareDeviceInfoMock.copy(version = version),
      firmwareUpdateState = FirmwareData.FirmwareUpdateState.UpToDate
    )
  }

  fun createFingerprintsAction(
    fingerprintCount: Int = 3,
    fingerprintResetReady: Boolean = true,
  ) = FingerprintsAction(
    gettingStartedTasks = emptyList(),
    fingerprintCount = fingerprintCount,
    firmwareDeviceInfo = FirmwareDeviceInfoMock,
    fingerprintResetReady = fingerprintResetReady
  )

  fun addFingerprintsActionToService(action: FingerprintsAction) {
    securityActionsService.actions.removeAll { it.type() == SecurityActionType.FINGERPRINTS }
    securityActionsService.actions += action
  }

  fun createSecurityHubScreen() =
    SecurityHubScreen(
      account = FullAccountMock,
      hardwareRecoveryData = LostHardwareRecoveryDataMock
    )

  fun createEekBackupHealthAction(
    status: EekBackupStatus = EekBackupStatus.Healthy(lastUploaded = clock.now()),
    featureState: FunctionalityFeatureStates.FeatureState = FunctionalityFeatureStates.FeatureState.Available,
  ) = EekBackupHealthAction(
    cloudBackupStatus = status,
    featureState = featureState
  )

  fun addEekActionToService(action: EekBackupHealthAction) {
    securityActionsService.actions.removeAll { it.type() == SecurityActionType.EEK_BACKUP }
    securityActionsService.actions += action
  }

  beforeTest {
    securityActionsService.clear()
    Router.route = null
    featureFlagDao.reset()
  }

  test("clicking firmware update navigates to the correct route") {
    presenter.test(createSecurityHubScreen()) {
      awaitBody<SecurityHubBodyModel> {
        onRecommendationClick(UPDATE_FIRMWARE)
      }
      it.goToCalls.awaitItem().shouldBeTypeOf<FwupScreen>()
    }
  }

  test("clicking EEK navigates to the education when not backed up") {
    presenter.test(createSecurityHubScreen()) {
      awaitBody<SecurityHubBodyModel> {
        onRecommendationClick(BACKUP_EAK)
      }
      it.goToCalls.awaitItem().shouldBeTypeOf<SecurityHubEducationScreen.RecommendationEducation>()
    }
  }

  test("clicking EEK navigates to the setting when backed up") {
    val action = createEekBackupHealthAction()
    addEekActionToService(action)

    presenter.test(createSecurityHubScreen()) {
      awaitUntilBody<SecurityHubBodyModel> {
        onSecurityActionClick(action)
      }
      it.goToCalls.awaitItem().shouldBeTypeOf<CloudBackupHealthDashboardScreen>()
    }
  }

  test("clicking a fingerprint navigates to the education screen") {
    presenter.test(createSecurityHubScreen()) {
      awaitBody<SecurityHubBodyModel> {
        onRecommendationClick(ADD_FINGERPRINTS)
      }
      it.goToCalls.awaitItem().shouldBeTypeOf<SecurityHubEducationScreen.RecommendationEducation>()
    }
  }

  test("tapping fingerprint action triggers manage fingerprints sheet") {
    val action = createFingerprintsAction()
    addFingerprintsActionToService(action)

    presenter.test(createSecurityHubScreen()) {
      awaitBody<SecurityHubBodyModel> {
        onSecurityActionClick(action)
      }

      it.showSheetCalls.awaitItem().shouldBeTypeOf<ManageFingerprintsOptionsSheet>()
    }
  }

  test("fingerprint reset feature disabled should pass false to ManageFingerprintsOptionsSheet") {
    setupFingerprintResetFeatureFlags(featureEnabled = false)

    val action = createFingerprintsAction()
    addFingerprintsActionToService(action)

    presenter.test(createSecurityHubScreen()) {
      awaitBody<SecurityHubBodyModel> {
        onSecurityActionClick(action)
      }

      it.showSheetCalls.awaitItem().run {
        shouldBeTypeOf<ManageFingerprintsOptionsSheet>()
        fingerprintResetEnabled.shouldBeFalse()
      }
    }
  }

  // TODO: fix flaky test
  xtest("fingerprint reset disabled when firmware version too old") {
    setupFingerprintResetFeatureFlags()
    setupFirmwareVersion("1.0.97")

    val action = createFingerprintsAction(
      fingerprintResetReady = false
    )
    addFingerprintsActionToService(action)

    presenter.test(createSecurityHubScreen()) {
      awaitUntilBody<SecurityHubBodyModel> {
        onSecurityActionClick(action)
      }

      it.showSheetCalls.awaitItem().run {
        shouldBeTypeOf<ManageFingerprintsOptionsSheet>()
        fingerprintResetEnabled.shouldBeFalse()
      }
    }
  }

  // TODO: fix flaky test
  xtest("fingerprint reset enabled when firmware version meets requirement") {
    setupFingerprintResetFeatureFlags()
    setupFirmwareVersion("1.0.98")

    val action = createFingerprintsAction(
      fingerprintResetReady = false
    )
    addFingerprintsActionToService(action)

    presenter.test(createSecurityHubScreen()) {
      awaitUntilBody<SecurityHubBodyModel> {
        onSecurityActionClick(action)
      }

      it.showSheetCalls.awaitItem().run {
        shouldBeTypeOf<ManageFingerprintsOptionsSheet>()
        fingerprintResetEnabled.shouldBeTrue()
      }
    }
  }

  test("fingerprintResetReady false should not show COMPLETE_FINGERPRINT_RESET recommendation") {
    setupFingerprintResetFeatureFlags()

    val action = createFingerprintsAction(
      fingerprintCount = 1,
      fingerprintResetReady = false
    )
    addFingerprintsActionToService(action)

    presenter.test(createSecurityHubScreen()) {
      awaitBody<SecurityHubBodyModel> {
        recommendations.none { it == COMPLETE_FINGERPRINT_RESET }.shouldBeTrue()
      }
    }
  }

  test("both ADD_FINGERPRINTS and COMPLETE_FINGERPRINT_RESET recommendations present") {
    setupFingerprintResetFeatureFlags()

    val action = createFingerprintsAction(fingerprintCount = 1)
    addFingerprintsActionToService(action)

    securityActionsService.recommendations.clear()
    securityActionsService.recommendations.addAll(action.getRecommendations())

    presenter.test(createSecurityHubScreen()) {
      awaitBody<SecurityHubBodyModel> {
        recommendations.contains(ADD_FINGERPRINTS).shouldBeTrue()
        recommendations.contains(COMPLETE_FINGERPRINT_RESET).shouldBeTrue()
      }
    }
  }

  test("clicking complete fingerprint reset transitions to fingerprint reset state") {
    setupFingerprintResetFeatureFlags()

    presenter.test(createSecurityHubScreen()) {
      awaitBody<SecurityHubBodyModel> {
        onRecommendationClick(COMPLETE_FINGERPRINT_RESET)
      }

      awaitBodyMock<FingerprintResetProps>(
        id = fingerprintResetUiStateMachine.id
      ) {
        account.shouldBe(FullAccountMock)
        onCancel()
      }

      awaitBody<SecurityHubBodyModel>()
    }
  }

  test("fingerprint reset onComplete callback navigates to manage fingerprints") {
    setupFingerprintResetFeatureFlags()

    presenter.test(createSecurityHubScreen()) {
      awaitBody<SecurityHubBodyModel> {
        onRecommendationClick(COMPLETE_FINGERPRINT_RESET)
      }

      awaitBodyMock<FingerprintResetProps>(
        id = fingerprintResetUiStateMachine.id
      ) {
        account.shouldBe(FullAccountMock)
        onComplete(
          EnrolledFingerprints(
            fingerprintHandles = listOf(
              FingerprintHandle(index = 0, label = "Fingerprint 1")
            )
          )
        )
      }

      it.goToCalls.awaitItem().shouldBeTypeOf<ManagingFingerprintsScreen>()
    }
  }

  test("fingerprint reset onFwUpRequired callback navigates to firmware update") {
    setupFingerprintResetFeatureFlags()

    presenter.test(createSecurityHubScreen()) {
      awaitBody<SecurityHubBodyModel> {
        onRecommendationClick(COMPLETE_FINGERPRINT_RESET)
      }

      awaitBodyMock<FingerprintResetProps>(
        id = fingerprintResetUiStateMachine.id
      ) {
        account.shouldBe(FullAccountMock)
        onFwUpRequired()
      }

      it.goToCalls.awaitItem().shouldBeTypeOf<FwupScreen>()
    }
  }

  test("recommendation maps to the correct navigation id") {
    entries.forEach { recommendation ->
      when (recommendation) {
        BACKUP_MOBILE_KEY -> recommendation.navigationScreenId()
          .shouldBe(NavigationScreenId.NAVIGATION_SCREEN_ID_MOBILE_KEY_BACKUP)
        BACKUP_EAK -> recommendation.navigationScreenId()
          .shouldBe(NavigationScreenId.NAVIGATION_SCREEN_ID_EAK_BACKUP_HEALTH)
        ADD_FINGERPRINTS -> recommendation.navigationScreenId()
          .shouldBe(NavigationScreenId.NAVIGATION_SCREEN_ID_MANAGE_FINGERPRINTS)
        COMPLETE_FINGERPRINT_RESET -> recommendation.navigationScreenId()
          .shouldBe(NavigationScreenId.NAVIGATION_SCREEN_ID_MANAGE_FINGERPRINTS)
        ADD_TRUSTED_CONTACTS -> recommendation.navigationScreenId()
          .shouldBe(NavigationScreenId.NAVIGATION_SCREEN_ID_MANAGE_RECOVERY_CONTACTS)
        ENABLE_CRITICAL_ALERTS, ENABLE_PUSH_NOTIFICATIONS, ENABLE_SMS_NOTIFICATIONS,
        ENABLE_EMAIL_NOTIFICATIONS,
        -> recommendation.navigationScreenId()
          .shouldBe(NavigationScreenId.NAVIGATION_SCREEN_ID_MANAGE_CRITICAL_ALERTS)
        ADD_BENEFICIARY -> recommendation.navigationScreenId()
          .shouldBe(NavigationScreenId.NAVIGATION_SCREEN_ID_MANAGE_INHERITANCE)
        SETUP_BIOMETRICS -> recommendation.navigationScreenId()
          .shouldBe(NavigationScreenId.NAVIGATION_SCREEN_ID_MANAGE_BIOMETRIC)
        PAIR_HARDWARE_DEVICE -> recommendation.navigationScreenId()
          .shouldBe(NavigationScreenId.NAVIGATION_SCREEN_ID_PAIR_DEVICE)
        UPDATE_FIRMWARE -> recommendation.navigationScreenId()
          .shouldBe(NavigationScreenId.NAVIGATION_SCREEN_ID_UPDATE_FIRMWARE)
        ENABLE_TRANSACTION_VERIFICATION -> recommendation.navigationScreenId()
          .shouldBe(NavigationScreenId.NAVIGATION_SCREEN_ID_TX_VERIFICATION_POLICY)
      }
    }
  }

  test("markAllRecommendationsViewed marks all NEW recommendations as VIEWED with timestamps") {
    securityActionsService.statuses = entries.mapIndexed { i, rec ->
      SecurityRecommendationWithStatus(
        recommendation = rec,
        interactionStatus = if (i % 2 == 0) SecurityInteractionStatus.NEW else SecurityInteractionStatus.VIEWED,
        lastRecommendationTriggeredAt = clock.now(),
        lastInteractedAt = if (i % 2 == 0) null else clock.now(),
        recordUpdatedAt = clock.now()
      )
    }
    runBlocking {
      securityActionsService.markAllRecommendationsViewed()
      securityActionsService.statuses.forEach { status ->
        status.interactionStatus shouldBe SecurityInteractionStatus.VIEWED
        (status.lastInteractedAt?.toEpochMilliseconds() ?: -1) shouldBe (status.recordUpdatedAt?.toEpochMilliseconds() ?: -2)
        status.lastInteractedAt shouldNotBe null
      }
    }
  }
})
