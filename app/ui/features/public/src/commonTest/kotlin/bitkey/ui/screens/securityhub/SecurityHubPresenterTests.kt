package bitkey.ui.screens.securityhub

import bitkey.privilegedactions.FingerprintResetF8eClientFake
import bitkey.privilegedactions.FingerprintResetServiceImpl
import bitkey.securitycenter.EekBackupHealthAction
import bitkey.securitycenter.SecurityActionRecommendation.*
import bitkey.securitycenter.SecurityActionType
import bitkey.securitycenter.SecurityActionsServiceFake
import bitkey.securitycenter.SecurityRecommendationWithStatus
import bitkey.ui.framework.test
import bitkey.ui.screens.securityhub.education.SecurityHubEducationScreen
import build.wallet.account.AccountServiceFake
import build.wallet.availability.AppFunctionalityServiceFake
import build.wallet.availability.FunctionalityFeatureStates
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.cloud.backup.health.EekBackupStatus
import build.wallet.compose.collections.immutableListOf
import build.wallet.coroutines.turbine.turbines
import build.wallet.database.SecurityInteractionStatus
import build.wallet.encrypt.SignatureUtilsMock
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.flags.FingerprintResetFeatureFlag
import build.wallet.fwup.FirmwareDataPendingUpdateMock
import build.wallet.fwup.FirmwareDataServiceFake
import build.wallet.navigation.v1.NavigationScreenId
import build.wallet.platform.haptics.HapticsMock
import build.wallet.router.Router
import build.wallet.statemachine.StateMachineMock
import build.wallet.statemachine.cloud.health.CloudBackupHealthDashboardScreen
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryDataMock
import build.wallet.statemachine.fwup.FwupScreen
import build.wallet.statemachine.moneyhome.card.CardModel
import build.wallet.statemachine.recovery.hardware.HardwareRecoveryStatusCardUiProps
import build.wallet.statemachine.recovery.hardware.HardwareRecoveryStatusCardUiStateMachine
import build.wallet.statemachine.recovery.hardware.fingerprintreset.FingerprintResetStatusCardUiProps
import build.wallet.statemachine.recovery.hardware.fingerprintreset.FingerprintResetStatusCardUiStateMachine
import build.wallet.statemachine.recovery.socrec.RecoveryContactCardsUiProps
import build.wallet.statemachine.recovery.socrec.RecoveryContactCardsUiStateMachine
import build.wallet.statemachine.settings.full.device.fingerprints.resetfingerprints.ResetFingerprintsProps
import build.wallet.statemachine.settings.full.device.fingerprints.resetfingerprints.ResetFingerprintsUiStateMachine
import build.wallet.statemachine.status.HomeStatusBannerUiProps
import build.wallet.statemachine.status.HomeStatusBannerUiStateMachine
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.time.ClockFake
import build.wallet.ui.model.status.StatusBannerModel
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeTypeOf
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock

class SecurityHubPresenterTests : FunSpec({
  val testClock = ClockFake()
  val securityActionsService = SecurityActionsServiceFake()
  val haptics = HapticsMock()
  val fingerprintResetService = FingerprintResetServiceImpl(
    privilegedActionF8eClient = FingerprintResetF8eClientFake(turbines::create, testClock),
    accountService = AccountServiceFake(),
    signatureUtils = SignatureUtilsMock(),
    clock = testClock
  )

  val firmwareDataService = FirmwareDataServiceFake()
  firmwareDataService.pendingUpdate = FirmwareDataPendingUpdateMock

  val featureFlagDao = FeatureFlagDaoFake()
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
    resetFingerprintsUiStateMachine = object : ResetFingerprintsUiStateMachine,
      StateMachineMock<ResetFingerprintsProps, ScreenModel?>(
        initialModel = null
      ) {},
    appFunctionalityService = AppFunctionalityServiceFake(),
    haptics = haptics,
    fingerprintResetFeatureFlag = FingerprintResetFeatureFlag(
      featureFlagDao = featureFlagDao
    )
  )

  beforeTest {
    securityActionsService.clear()
    Router.route = null
  }

  test("clicking firmware update navigates to the correct route") {
    presenter.test(
      SecurityHubScreen(
        account = FullAccountMock,
        hardwareRecoveryData = LostHardwareRecoveryDataMock
      )
    ) {
      awaitBody<SecurityHubBodyModel> {
        onRecommendationClick(UPDATE_FIRMWARE)
      }
      it.goToCalls.awaitItem().shouldBeTypeOf<FwupScreen>()
    }
  }

  test("clicking EEK navigates to the education when not backed up") {
    presenter.test(
      SecurityHubScreen(
        account = FullAccountMock,
        hardwareRecoveryData = LostHardwareRecoveryDataMock
      )
    ) {
      awaitBody<SecurityHubBodyModel> {
        onRecommendationClick(BACKUP_EAK)
      }
      it.goToCalls.awaitItem().shouldBeTypeOf<SecurityHubEducationScreen.RecommendationEducation>()
    }
  }

  test("clicking EEK navigates to the setting when backed up") {
    val action = EekBackupHealthAction(
      cloudBackupStatus = EekBackupStatus.Healthy(lastUploaded = Clock.System.now()),
      featureState = FunctionalityFeatureStates.FeatureState.Available
    )
    securityActionsService.actions.removeAll { it.type() == SecurityActionType.EEK_BACKUP }
    securityActionsService.actions += action
    presenter.test(
      SecurityHubScreen(
        account = FullAccountMock,
        hardwareRecoveryData = LostHardwareRecoveryDataMock
      )
    ) {
      awaitUntilBody<SecurityHubBodyModel> {
        onSecurityActionClick(action)
      }
      it.goToCalls.awaitItem().shouldBeTypeOf<CloudBackupHealthDashboardScreen>()
    }
  }

  test("clicking a fingerprint navigates to the education screen") {
    presenter.test(
      SecurityHubScreen(
        account = FullAccountMock,
        hardwareRecoveryData = LostHardwareRecoveryDataMock
      )
    ) {
      awaitBody<SecurityHubBodyModel> {
        onRecommendationClick(ADD_FINGERPRINTS)
      }
      it.goToCalls.awaitItem().shouldBeTypeOf<SecurityHubEducationScreen.RecommendationEducation>()
    }
  }

  test("clicking complete fingerprint reset navigates to reset fingerprints state") {
    presenter.test(
      SecurityHubScreen(
        account = FullAccountMock,
        hardwareRecoveryData = LostHardwareRecoveryDataMock
      )
    ) {
      awaitBody<SecurityHubBodyModel> {
        onRecommendationClick(COMPLETE_FINGERPRINT_RESET)
      }

      // After click, UI should transition to ResetFingerprints state, so no navigation occurs.
      it.goToCalls.expectNoEvents()
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
        lastRecommendationTriggeredAt = testClock.now(),
        lastInteractedAt = if (i % 2 == 0) null else testClock.now(),
        recordUpdatedAt = testClock.now()
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
