package build.wallet.statemachine.data.account.create.onboard

import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.money.display.CurrencyPreferenceDataMock
import build.wallet.onboarding.OnboardingKeyboxSealedCsekDaoMock
import build.wallet.onboarding.OnboardingKeyboxStep.CloudBackup
import build.wallet.onboarding.OnboardingKeyboxStep.NotificationPreferences
import build.wallet.onboarding.OnboardingKeyboxStepState.Complete
import build.wallet.onboarding.OnboardingKeyboxStepStateDaoFake
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.account.CreateFullAccountData.OnboardKeyboxDataFull.BackingUpKeyboxToCloudDataFull
import build.wallet.statemachine.data.account.CreateFullAccountData.OnboardKeyboxDataFull.CompletingCloudBackupDataFull
import build.wallet.statemachine.data.account.CreateFullAccountData.OnboardKeyboxDataFull.CompletingNotificationsDataFull
import build.wallet.statemachine.data.account.CreateFullAccountData.OnboardKeyboxDataFull.LoadingInitialStepDataFull
import build.wallet.statemachine.data.account.CreateFullAccountData.OnboardKeyboxDataFull.SettingNotificationsPreferencesDataFull
import build.wallet.statemachine.data.account.OnboardConfig
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import kotlinx.coroutines.flow.first
import okio.ByteString

class OnboardKeyboxDataStateMachineImplTests : FunSpec({

  val onboardingKeyboxSealedCsekDao = OnboardingKeyboxSealedCsekDaoMock()
  val onboardingKeyboxStepStateDao = OnboardingKeyboxStepStateDaoFake()

  val dataStateMachine =
    OnboardKeyboxDataStateMachineImpl(
      onboardingKeyboxSealedCsekDao = onboardingKeyboxSealedCsekDao,
      onboardingKeyboxStepStateDao = onboardingKeyboxStepStateDao
    )

  val props =
    OnboardKeyboxDataProps(
      keybox = KeyboxMock,
      onboardConfig = OnboardConfig(stepsToSkip = emptySet()),
      currencyPreferenceData = CurrencyPreferenceDataMock,
      onExistingAppDataFound = { _, proceed -> proceed() },
      isSkipCloudBackupInstructions = false
    )

  beforeTest {
    onboardingKeyboxStepStateDao.clear()
    onboardingKeyboxSealedCsekDao.clear()
  }

  test("onboard new keybox successfully") {
    onboardingKeyboxSealedCsekDao.sealedCsek = ByteString.EMPTY
    dataStateMachine.test(props) {
      awaitItem().shouldBeTypeOf<LoadingInitialStepDataFull>()

      awaitItem().let {
        it.shouldBeTypeOf<BackingUpKeyboxToCloudDataFull>()
        it.onBackupSaved()
      }

      awaitItem().let {
        it.shouldBeTypeOf<CompletingCloudBackupDataFull>()
      }

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
    dataStateMachine.test(
      props.copy(onboardConfig = OnboardConfig(stepsToSkip = setOf(CloudBackup)))
    ) {
      awaitItem().shouldBeTypeOf<LoadingInitialStepDataFull>()
      awaitItem().shouldBeTypeOf<CompletingCloudBackupDataFull>()

      onboardingKeyboxStepStateDao.stateForStep(CloudBackup).first().shouldBe(Complete)

      awaitItem().shouldBeTypeOf<SettingNotificationsPreferencesDataFull>()
    }
  }

  test("skip notifications") {
    onboardingKeyboxStepStateDao.setStateForStep(CloudBackup, Complete)
    dataStateMachine.test(
      props.copy(onboardConfig = OnboardConfig(stepsToSkip = setOf(NotificationPreferences)))
    ) {
      awaitItem().shouldBeTypeOf<LoadingInitialStepDataFull>()
      awaitItem().shouldBeTypeOf<CompletingNotificationsDataFull>()

      onboardingKeyboxStepStateDao.stateForStep(NotificationPreferences).first().shouldBe(Complete)
    }
  }

  test("skip all steps") {
    dataStateMachine.test(
      props.copy(
        onboardConfig =
          OnboardConfig(
            stepsToSkip = setOf(CloudBackup, NotificationPreferences)
          )
      )
    ) {
      awaitItem().shouldBeTypeOf<LoadingInitialStepDataFull>()
      awaitItem().shouldBeTypeOf<CompletingCloudBackupDataFull>()
      awaitItem().shouldBeTypeOf<CompletingNotificationsDataFull>()

      onboardingKeyboxStepStateDao.stateForStep(CloudBackup).first().shouldBe(Complete)
      onboardingKeyboxStepStateDao.stateForStep(NotificationPreferences).first().shouldBe(Complete)
    }
  }
})
