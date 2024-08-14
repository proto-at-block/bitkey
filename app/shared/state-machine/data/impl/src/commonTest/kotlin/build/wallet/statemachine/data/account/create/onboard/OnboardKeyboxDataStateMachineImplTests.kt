package build.wallet.statemachine.data.account.create.onboard

import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.debug.DebugOptionsServiceFake
import build.wallet.onboarding.OnboardingKeyboxSealedCsekDaoMock
import build.wallet.onboarding.OnboardingKeyboxStep.CloudBackup
import build.wallet.onboarding.OnboardingKeyboxStep.NotificationPreferences
import build.wallet.onboarding.OnboardingKeyboxStepState.Complete
import build.wallet.onboarding.OnboardingKeyboxStepStateDaoFake
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.account.CreateFullAccountData.OnboardKeyboxDataFull.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import kotlinx.coroutines.flow.first
import okio.ByteString

class OnboardKeyboxDataStateMachineImplTests : FunSpec({

  val onboardingKeyboxSealedCsekDao = OnboardingKeyboxSealedCsekDaoMock()
  val onboardingKeyboxStepStateDao = OnboardingKeyboxStepStateDaoFake()
  val debugOptionsService = DebugOptionsServiceFake()

  val dataStateMachine = OnboardKeyboxDataStateMachineImpl(
    onboardingKeyboxSealedCsekDao = onboardingKeyboxSealedCsekDao,
    onboardingKeyboxStepStateDao = onboardingKeyboxStepStateDao,
    debugOptionsService = debugOptionsService
  )

  val props = OnboardKeyboxDataProps(
    keybox = KeyboxMock,
    onExistingAppDataFound = { _, proceed -> proceed() },
    isSkipCloudBackupInstructions = false
  )

  beforeTest {
    onboardingKeyboxStepStateDao.clear()
    onboardingKeyboxSealedCsekDao.clear()
    debugOptionsService.reset()
  }

  test("onboard new keybox successfully") {
    onboardingKeyboxSealedCsekDao.sealedCsek = ByteString.EMPTY
    dataStateMachine.test(props) {
      awaitItem().shouldBeTypeOf<LoadingInitialStepDataFull>()

      awaitItem().let {
        it.shouldBeTypeOf<BackingUpKeyboxToCloudDataFull>()
        it.onBackupSaved()
      }

      awaitItem().shouldBeTypeOf<CompletingCloudBackupDataFull>()

      onboardingKeyboxSealedCsekDao.sealedCsek.shouldBeNull()
      onboardingKeyboxStepStateDao.stateForStep(CloudBackup).first().shouldBe(Complete)

      awaitItem().let {
        it.shouldBeTypeOf<SettingNotificationsPreferencesDataFull>()
        it.onComplete()
      }

      awaitItem().shouldBeTypeOf<CompletingNotificationsDataFull>()
      onboardingKeyboxStepStateDao.stateForStep(NotificationPreferences).first().shouldBe(Complete)
    }
  }

  test("initial step should be cloud backup") {
    onboardingKeyboxSealedCsekDao.sealedCsek = ByteString.EMPTY
    dataStateMachine.test(props) {
      awaitItem().shouldBeTypeOf<LoadingInitialStepDataFull>()
      awaitItem().shouldBeTypeOf<BackingUpKeyboxToCloudDataFull>()
    }
  }

  test("initial step when missing sealed CSEK should be cloud back up") {
    onboardingKeyboxSealedCsekDao.sealedCsek = null
    dataStateMachine.test(props) {
      awaitItem().shouldBeTypeOf<LoadingInitialStepDataFull>()
      awaitItem().shouldBeTypeOf<BackingUpKeyboxToCloudDataFull>()
    }
  }

  test("initial step when cloud backup complete should be notifications") {
    onboardingKeyboxStepStateDao.setStateForStep(CloudBackup, Complete)
    dataStateMachine.test(props) {
      awaitItem().shouldBeTypeOf<LoadingInitialStepDataFull>()
      awaitItem().shouldBeTypeOf<SettingNotificationsPreferencesDataFull>()
    }
  }

  test("initial step when cloud backup and notifications complete should be completing notifications") {
    onboardingKeyboxStepStateDao.setStateForStep(CloudBackup, Complete)
    onboardingKeyboxStepStateDao.setStateForStep(NotificationPreferences, Complete)
    dataStateMachine.test(props) {
      awaitItem().shouldBeTypeOf<LoadingInitialStepDataFull>()
      awaitItem().shouldBeTypeOf<CompletingNotificationsDataFull>()
    }
  }

  test("skip cloud backup") {
    debugOptionsService.setSkipCloudBackupOnboarding(value = true)
    dataStateMachine.test(props) {
      awaitItem().shouldBeTypeOf<LoadingInitialStepDataFull>()
      awaitItem().shouldBeTypeOf<CompletingCloudBackupDataFull>()

      onboardingKeyboxStepStateDao.stateForStep(CloudBackup).first().shouldBe(Complete)

      awaitItem().shouldBeTypeOf<SettingNotificationsPreferencesDataFull>()
    }
  }

  test("skip notifications") {
    onboardingKeyboxStepStateDao.setStateForStep(CloudBackup, Complete)
    debugOptionsService.setSkipNotificationsOnboarding(value = true)
    dataStateMachine.test(props) {
      awaitItem().shouldBeTypeOf<LoadingInitialStepDataFull>()
      awaitItem().shouldBeTypeOf<CompletingNotificationsDataFull>()

      onboardingKeyboxStepStateDao.stateForStep(NotificationPreferences).first().shouldBe(Complete)
    }
  }

  test("skip all steps") {
    debugOptionsService.setSkipCloudBackupOnboarding(value = true)
    debugOptionsService.setSkipNotificationsOnboarding(value = true)
    dataStateMachine.test(props) {
      awaitItem().shouldBeTypeOf<LoadingInitialStepDataFull>()
      awaitItem().shouldBeTypeOf<CompletingCloudBackupDataFull>()
      awaitItem().shouldBeTypeOf<CompletingNotificationsDataFull>()

      onboardingKeyboxStepStateDao.stateForStep(CloudBackup).first().shouldBe(Complete)
      onboardingKeyboxStepStateDao.stateForStep(NotificationPreferences).first().shouldBe(Complete)
    }
  }
})
