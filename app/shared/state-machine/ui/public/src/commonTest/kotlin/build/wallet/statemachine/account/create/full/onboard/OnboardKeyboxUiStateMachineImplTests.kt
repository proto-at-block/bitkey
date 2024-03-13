package build.wallet.statemachine.account.create.full.onboard

import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.feature.FeatureFlagDaoMock
import build.wallet.feature.FeatureFlagValue
import build.wallet.keybox.KeyboxDaoMock
import build.wallet.onboarding.OnboardingKeyboxSealedCsekDaoMock
import build.wallet.onboarding.OnboardingKeyboxStepStateDaoMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.account.create.full.OnboardKeyboxUiProps
import build.wallet.statemachine.account.create.full.OnboardKeyboxUiStateMachineImpl
import build.wallet.statemachine.account.create.full.onboard.notifications.NotificationPreferencesSetupUiProps
import build.wallet.statemachine.account.create.full.onboard.notifications.NotificationPreferencesSetupUiPropsV2
import build.wallet.statemachine.account.create.full.onboard.notifications.NotificationPreferencesSetupUiStateMachine
import build.wallet.statemachine.account.create.full.onboard.notifications.NotificationPreferencesSetupUiStateMachineV2
import build.wallet.statemachine.cloud.FullAccountCloudSignInAndBackupProps
import build.wallet.statemachine.cloud.FullAccountCloudSignInAndBackupUiStateMachine
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.awaitScreenWithBody
import build.wallet.statemachine.core.awaitScreenWithBodyModelMock
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.account.CreateFullAccountData.OnboardKeyboxDataFull.BackingUpKeyboxToCloudDataFull
import build.wallet.statemachine.data.account.CreateFullAccountData.OnboardKeyboxDataFull.CompletingCloudBackupDataFull
import build.wallet.statemachine.data.account.CreateFullAccountData.OnboardKeyboxDataFull.CompletingNotificationsDataFull
import build.wallet.statemachine.data.account.CreateFullAccountData.OnboardKeyboxDataFull.FailedCloudBackupDataFull
import build.wallet.statemachine.data.account.CreateFullAccountData.OnboardKeyboxDataFull.SettingNotificationsPreferencesDataFull
import build.wallet.statemachine.settings.full.notifications.NotificationsFlowV2EnabledFeatureFlag
import build.wallet.statemachine.ui.clickPrimaryButton
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import okio.ByteString

class OnboardKeyboxUiStateMachineImplTests : FunSpec({

  val onboardingKeyboxSealedCsekDao = OnboardingKeyboxSealedCsekDaoMock()
  val onboardingKeyboxStepStateDao =
    OnboardingKeyboxStepStateDaoMock(turbines::create)

  val keyboxDao = KeyboxDaoMock(turbines::create)
  val v2FeatureFlag = NotificationsFlowV2EnabledFeatureFlag(FeatureFlagDaoMock())

  val stateMachine =
    OnboardKeyboxUiStateMachineImpl(
      fullAccountCloudSignInAndBackupUiStateMachine =
        object : FullAccountCloudSignInAndBackupUiStateMachine, ScreenStateMachineMock<FullAccountCloudSignInAndBackupProps>(
          id = "cloud"
        ) {},
      notificationsFlowV2EnabledFeatureFlag = v2FeatureFlag,
      notificationPreferencesSetupUiStateMachine =
        object : NotificationPreferencesSetupUiStateMachine,
          ScreenStateMachineMock<NotificationPreferencesSetupUiProps>(
            id = "notification-preferences"
          ) {},
      notificationPreferencesSetupUiStateMachineV2 =
        object : NotificationPreferencesSetupUiStateMachineV2,
          ScreenStateMachineMock<NotificationPreferencesSetupUiPropsV2>(
            id = "notification-preferences-v2"
          ) {}
    )

  val onBackupSavedCalls = turbines.create<Unit>("onBackupSaved calls")
  val onBackupFailedCalls = turbines.create<Unit>("onBackupFailed calls")
  val backingUpKeyboxToCloudData =
    BackingUpKeyboxToCloudDataFull(
      keybox = KeyboxMock,
      sealedCsek = ByteString.EMPTY,
      onBackupSaved = { onBackupSavedCalls.add(Unit) },
      onBackupFailed = { onBackupFailedCalls.add(Unit) },
      isSkipCloudBackupInstructions = false
    )

  val failedCloudBackupDataRetryCalls = turbines.create<Unit>("FailedCloudBackupData retry calls")
  val failedCloudBackupData =
    FailedCloudBackupDataFull(
      retry = {
        failedCloudBackupDataRetryCalls.add(Unit)
      }
    )

  val settingNotificationsPreferencesDataCompleteCalls =
    turbines.create<Unit>(
      "SettingNotificationsPreferencesData complete calls"
    )
  val settingNotificationsPreferencesData =
    SettingNotificationsPreferencesDataFull(
      keybox = KeyboxMock,
      onComplete = { settingNotificationsPreferencesDataCompleteCalls.add(Unit) }
    )

  beforeTest {
    onboardingKeyboxSealedCsekDao.clear()
    onboardingKeyboxStepStateDao.reset()
    keyboxDao.reset()
  }

  beforeEach {
    v2FeatureFlag.apply {
      setFlagValue(FeatureFlagValue.BooleanFlag(false))
    }
  }

  test("BackingUpKeyboxToCloudData screen - onBackupSaved") {
    stateMachine.test(OnboardKeyboxUiProps(backingUpKeyboxToCloudData)) {
      awaitScreenWithBodyModelMock<FullAccountCloudSignInAndBackupProps> {
        onBackupSaved()
      }
      onBackupSavedCalls.awaitItem()
    }
  }

  test("BackingUpKeyboxToCloudData screen - onBackupFailed") {
    stateMachine.test(OnboardKeyboxUiProps(backingUpKeyboxToCloudData)) {
      awaitScreenWithBodyModelMock<FullAccountCloudSignInAndBackupProps> {
        onBackupFailed()
      }
      onBackupFailedCalls.awaitItem()
    }
  }

  test("FailedCloudBackupData screen") {
    stateMachine.test(OnboardKeyboxUiProps(failedCloudBackupData)) {
      awaitScreenWithBody<FormBodyModel> {
        clickPrimaryButton()
      }
      failedCloudBackupDataRetryCalls.awaitItem()
    }
  }

  test("CompletingCloudBackupData screen") {
    stateMachine.test(OnboardKeyboxUiProps(CompletingCloudBackupDataFull)) {
      awaitScreenWithBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
    }
  }

  test("SettingNotificationsPreferencesData screen - complete") {
    stateMachine.test(OnboardKeyboxUiProps(settingNotificationsPreferencesData)) {
      awaitScreenWithBodyModelMock<NotificationPreferencesSetupUiProps> {
        onComplete()
      }
      settingNotificationsPreferencesDataCompleteCalls.awaitItem()
    }
  }

  test("CompletingNotificationsData screen") {
    stateMachine.test(OnboardKeyboxUiProps(CompletingNotificationsDataFull)) {
      awaitScreenWithBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
    }
  }
})
