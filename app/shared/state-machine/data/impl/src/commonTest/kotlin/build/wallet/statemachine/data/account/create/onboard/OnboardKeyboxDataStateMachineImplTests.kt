package build.wallet.statemachine.data.account.create.onboard

import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.feature.FeatureFlagDaoMock
import build.wallet.feature.setFlagValue
import build.wallet.money.MultipleFiatCurrencyEnabledFeatureFlag
import build.wallet.money.display.CurrencyPreferenceDataMock
import build.wallet.onboarding.OnboardingKeyboxSealedCsekDaoMock
import build.wallet.onboarding.OnboardingKeyboxStep.CloudBackup
import build.wallet.onboarding.OnboardingKeyboxStep.CurrencyPreference
import build.wallet.onboarding.OnboardingKeyboxStep.NotificationPreferences
import build.wallet.onboarding.OnboardingKeyboxStepState.Complete
import build.wallet.onboarding.OnboardingKeyboxStepStateDaoFake
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.account.CreateFullAccountData.OnboardKeyboxDataFull.BackingUpKeyboxToCloudDataFull
import build.wallet.statemachine.data.account.CreateFullAccountData.OnboardKeyboxDataFull.CompletingCloudBackupDataFull
import build.wallet.statemachine.data.account.CreateFullAccountData.OnboardKeyboxDataFull.CompletingCurrencyPreferenceDataFull
import build.wallet.statemachine.data.account.CreateFullAccountData.OnboardKeyboxDataFull.CompletingNotificationsDataFull
import build.wallet.statemachine.data.account.CreateFullAccountData.OnboardKeyboxDataFull.LoadingInitialStepDataFull
import build.wallet.statemachine.data.account.CreateFullAccountData.OnboardKeyboxDataFull.SettingCurrencyPreferenceDataFull
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
  val multipleFiatCurrencyEnabledFeatureFlag =
    MultipleFiatCurrencyEnabledFeatureFlag(
      featureFlagDao = FeatureFlagDaoMock()
    )

  val dataStateMachine =
    OnboardKeyboxDataStateMachineImpl(
      onboardingKeyboxSealedCsekDao = onboardingKeyboxSealedCsekDao,
      onboardingKeyboxStepStateDao = onboardingKeyboxStepStateDao,
      multipleFiatCurrencyEnabledFeatureFlag = multipleFiatCurrencyEnabledFeatureFlag
    )

  val props =
    OnboardKeyboxDataProps(
      keybox = KeyboxMock,
      onboardConfig = OnboardConfig(stepsToSkip = emptySet()),
      currencyPreferenceData = CurrencyPreferenceDataMock,
      onExistingCloudBackupFound = { _, proceed -> proceed() },
      isSkipCloudBackupInstructions = false
    )

  beforeTest {
    onboardingKeyboxStepStateDao.clear()
    onboardingKeyboxSealedCsekDao.clear()
  }

  test("onboard new keybox successfully") {
    multipleFiatCurrencyEnabledFeatureFlag.setFlagValue(false)
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

  test("onboard new keybox successfully with currency step") {
    multipleFiatCurrencyEnabledFeatureFlag.setFlagValue(true)
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

      awaitItem().let {
        it.shouldBeTypeOf<SettingCurrencyPreferenceDataFull>()
        it.onComplete()
      }

      awaitItem().shouldBeTypeOf<CompletingCurrencyPreferenceDataFull>()
      onboardingKeyboxStepStateDao.stateForStep(CurrencyPreference).first().shouldBe(Complete)
    }
  }

  test("initial step should be cloud backup") {
    multipleFiatCurrencyEnabledFeatureFlag.setFlagValue(false)
    onboardingKeyboxSealedCsekDao.sealedCsek = ByteString.EMPTY
    dataStateMachine.test(props) {
      awaitItem().shouldBeTypeOf<LoadingInitialStepDataFull>()
      awaitItem().shouldBeTypeOf<BackingUpKeyboxToCloudDataFull>()
    }
  }

  test("initial step when missing sealed CSEK should be cloud back up") {
    multipleFiatCurrencyEnabledFeatureFlag.setFlagValue(false)
    onboardingKeyboxSealedCsekDao.sealedCsek = null
    dataStateMachine.test(props) {
      awaitItem().shouldBeTypeOf<LoadingInitialStepDataFull>()
      awaitItem().shouldBeTypeOf<BackingUpKeyboxToCloudDataFull>()
    }
  }

  test("initial step when cloud backup complete should be notifications") {
    multipleFiatCurrencyEnabledFeatureFlag.setFlagValue(false)
    onboardingKeyboxStepStateDao.setStateForStep(CloudBackup, Complete)
    dataStateMachine.test(props) {
      awaitItem().shouldBeTypeOf<LoadingInitialStepDataFull>()
      awaitItem().shouldBeTypeOf<SettingNotificationsPreferencesDataFull>()
    }
  }

  test("initial step when cloud backup and notifications complete should be currency") {
    multipleFiatCurrencyEnabledFeatureFlag.setFlagValue(true)
    onboardingKeyboxStepStateDao.setStateForStep(CloudBackup, Complete)
    onboardingKeyboxStepStateDao.setStateForStep(NotificationPreferences, Complete)
    dataStateMachine.test(props) {
      awaitItem().shouldBeTypeOf<LoadingInitialStepDataFull>()
      awaitItem().shouldBeTypeOf<SettingCurrencyPreferenceDataFull>()
    }
  }

  test("skip cloud backup") {
    multipleFiatCurrencyEnabledFeatureFlag.setFlagValue(false)
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
    multipleFiatCurrencyEnabledFeatureFlag.setFlagValue(false)
    onboardingKeyboxStepStateDao.setStateForStep(CloudBackup, Complete)
    dataStateMachine.test(
      props.copy(onboardConfig = OnboardConfig(stepsToSkip = setOf(NotificationPreferences)))
    ) {
      awaitItem().shouldBeTypeOf<LoadingInitialStepDataFull>()
      awaitItem().shouldBeTypeOf<CompletingNotificationsDataFull>()

      onboardingKeyboxStepStateDao.stateForStep(NotificationPreferences).first().shouldBe(Complete)
    }
  }

  test("skip notifications with currency step") {
    multipleFiatCurrencyEnabledFeatureFlag.setFlagValue(true)
    onboardingKeyboxStepStateDao.setStateForStep(CloudBackup, Complete)
    dataStateMachine.test(
      props.copy(onboardConfig = OnboardConfig(stepsToSkip = setOf(NotificationPreferences)))
    ) {
      awaitItem().shouldBeTypeOf<LoadingInitialStepDataFull>()
      awaitItem().shouldBeTypeOf<CompletingNotificationsDataFull>()

      onboardingKeyboxStepStateDao.stateForStep(NotificationPreferences).first().shouldBe(Complete)

      awaitItem().shouldBeTypeOf<SettingCurrencyPreferenceDataFull>()
    }
  }

  test("skip currency") {
    multipleFiatCurrencyEnabledFeatureFlag.setFlagValue(true)
    onboardingKeyboxStepStateDao.setStateForStep(CloudBackup, Complete)
    onboardingKeyboxStepStateDao.setStateForStep(NotificationPreferences, Complete)
    dataStateMachine.test(
      props.copy(onboardConfig = OnboardConfig(stepsToSkip = setOf(CurrencyPreference)))
    ) {
      awaitItem().shouldBeTypeOf<LoadingInitialStepDataFull>()
      awaitItem().shouldBeTypeOf<CompletingCurrencyPreferenceDataFull>()

      onboardingKeyboxStepStateDao.stateForStep(CurrencyPreference).first().shouldBe(Complete)
    }
  }

  test("skip all steps") {
    multipleFiatCurrencyEnabledFeatureFlag.setFlagValue(true)
    dataStateMachine.test(
      props.copy(
        onboardConfig =
          OnboardConfig(
            stepsToSkip = setOf(CloudBackup, CurrencyPreference, NotificationPreferences)
          )
      )
    ) {
      awaitItem().shouldBeTypeOf<LoadingInitialStepDataFull>()
      awaitItem().shouldBeTypeOf<CompletingCloudBackupDataFull>()
      awaitItem().shouldBeTypeOf<CompletingNotificationsDataFull>()
      awaitItem().shouldBeTypeOf<CompletingCurrencyPreferenceDataFull>()

      onboardingKeyboxStepStateDao.stateForStep(CloudBackup).first().shouldBe(Complete)
      onboardingKeyboxStepStateDao.stateForStep(NotificationPreferences).first().shouldBe(Complete)
      onboardingKeyboxStepStateDao.stateForStep(CurrencyPreference).first().shouldBe(Complete)
    }
  }
})
