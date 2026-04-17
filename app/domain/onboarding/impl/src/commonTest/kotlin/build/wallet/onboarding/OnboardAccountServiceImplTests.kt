package build.wallet.onboarding

import bitkey.account.AccountConfigServiceFake
import bitkey.account.HardwareType
import build.wallet.account.AccountServiceFake
import build.wallet.bitkey.keybox.FullAccountConfigMock
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.cloud.backup.csek.SealedCsekFake
import build.wallet.cloud.backup.csek.SealedSsekFake
import build.wallet.onboarding.OnboardAccountStep.*
import build.wallet.onboarding.OnboardingKeyboxStepState.Complete
import build.wallet.onboarding.OnboardingKeyboxStepState.Incomplete
import build.wallet.testing.shouldBeOk
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import kotlinx.coroutines.flow.first

class OnboardAccountServiceImplTests : FunSpec({
  val accountConfigService = AccountConfigServiceFake()
  val accountService = AccountServiceFake()
  val onboardingKeyboxStepStateDao = OnboardingKeyboxStepStateDaoFake()
  val onboardingKeyboxSealedCsekDao = OnboardingKeyboxSealedCsekDaoMock()
  val onboardingKeyboxSealedSsekDao = OnboardingKeyboxSealedSsekDaoFake()
  val onboardingCompletionService = OnboardingCompletionServiceFake()

  val service = OnboardAccountServiceImpl(
    accountConfigService = accountConfigService,
    accountService = accountService,
    onboardingKeyboxStepStateDao = onboardingKeyboxStepStateDao,
    onboardingKeyboxSealedCsekDao = onboardingKeyboxSealedCsekDao,
    onboardingKeyboxSealedSsekDao = onboardingKeyboxSealedSsekDao,
    onboardingCompletionService = onboardingCompletionService
  )

  beforeTest {
    accountConfigService.reset()
    accountService.reset()
    onboardingKeyboxStepStateDao.clear()
    onboardingKeyboxSealedCsekDao.reset()
    onboardingKeyboxSealedSsekDao.reset()
    onboardingCompletionService.reset()
  }

  test("marks descriptor backup step as incomplete") {
    // Given a completed descriptor backup step
    onboardingKeyboxStepStateDao
      .setStateForStep(OnboardingKeyboxStep.DescriptorBackup, Complete)
      .shouldBeOk()

    // When marking it incomplete
    val result = service.markStepIncomplete(DescriptorBackup(sealedSsek = SealedSsekFake))

    // Then the result is successful
    result.shouldBeOk()

    // And the step state is now incomplete
    val state = onboardingKeyboxStepStateDao
      .stateForStep(OnboardingKeyboxStep.DescriptorBackup)
      .first()
    state.shouldBe(Incomplete)
  }

  test("marks cloud backup step as incomplete") {
    // Given a completed cloud backup step
    onboardingKeyboxStepStateDao
      .setStateForStep(OnboardingKeyboxStep.CloudBackup, Complete)
      .shouldBeOk()

    // When marking it incomplete
    val result = service.markStepIncomplete(CloudBackup(sealedCsek = SealedCsekFake))

    // Then the result is successful
    result.shouldBeOk()

    // And the step state is now incomplete
    val state = onboardingKeyboxStepStateDao
      .stateForStep(OnboardingKeyboxStep.CloudBackup)
      .first()
    state.shouldBe(Incomplete)
  }

  test("marks notification preferences step as incomplete") {
    // Given a completed notification preferences step
    onboardingKeyboxStepStateDao
      .setStateForStep(OnboardingKeyboxStep.NotificationPreferences, Complete)
      .shouldBeOk()

    // When marking it incomplete
    val result = service.markStepIncomplete(NotificationPreferences)

    // Then the result is successful
    result.shouldBeOk()

    // And the step state is now incomplete
    val state = onboardingKeyboxStepStateDao
      .stateForStep(OnboardingKeyboxStep.NotificationPreferences)
      .first()
    state.shouldBe(Incomplete)
  }

  test("marks build hardware descriptor step as incomplete") {
    // Given a completed build hardware descriptor step
    onboardingKeyboxStepStateDao
      .setStateForStep(OnboardingKeyboxStep.BuildHardwareDescriptor, Complete)
      .shouldBeOk()

    // When marking it incomplete
    val result = service.markStepIncomplete(BuildHardwareDescriptor)

    // Then the result is successful
    result.shouldBeOk()

    // And the step state is now incomplete
    val state = onboardingKeyboxStepStateDao
      .stateForStep(OnboardingKeyboxStep.BuildHardwareDescriptor)
      .first()
    state.shouldBe(Incomplete)
  }

  test("pendingStep returns BuildHardwareDescriptor for W3 hardware after other steps complete") {
    // Given a W3 hardware account
    val w3Config = FullAccountConfigMock.copy(hardwareType = HardwareType.W3)
    val w3Account = FullAccountMock.copy(
      keybox = KeyboxMock.copy(config = w3Config)
    )
    accountService.setActiveAccount(w3Account)

    // And all previous steps are complete
    onboardingKeyboxStepStateDao
      .setStateForStep(OnboardingKeyboxStep.DescriptorBackup, Complete)
      .shouldBeOk()
    onboardingKeyboxStepStateDao
      .setStateForStep(OnboardingKeyboxStep.CloudBackup, Complete)
      .shouldBeOk()
    onboardingKeyboxStepStateDao
      .setStateForStep(OnboardingKeyboxStep.NotificationPreferences, Complete)
      .shouldBeOk()

    // When getting the pending step
    val pendingStep = service.pendingStep().shouldBeOk()

    // Then BuildHardwareDescriptor is returned
    pendingStep.shouldBeTypeOf<BuildHardwareDescriptor>()
  }

  test("pendingStep skips BuildHardwareDescriptor for W1 hardware") {
    // Given a W1 hardware account (default)
    val w1Config = FullAccountConfigMock.copy(hardwareType = HardwareType.W1)
    val w1Account = FullAccountMock.copy(
      keybox = KeyboxMock.copy(config = w1Config)
    )
    accountService.setActiveAccount(w1Account)

    // And all previous steps are complete
    onboardingKeyboxStepStateDao
      .setStateForStep(OnboardingKeyboxStep.DescriptorBackup, Complete)
      .shouldBeOk()
    onboardingKeyboxStepStateDao
      .setStateForStep(OnboardingKeyboxStep.CloudBackup, Complete)
      .shouldBeOk()
    onboardingKeyboxStepStateDao
      .setStateForStep(OnboardingKeyboxStep.NotificationPreferences, Complete)
      .shouldBeOk()

    // When getting the pending step
    val pendingStep = service.pendingStep().shouldBeOk()

    // Then null is returned (onboarding complete)
    pendingStep.shouldBe(null)
  }

  test("pendingStep returns null after BuildHardwareDescriptor is complete for W3 hardware") {
    // Given a W3 hardware account
    val w3Config = FullAccountConfigMock.copy(hardwareType = HardwareType.W3)
    val w3Account = FullAccountMock.copy(
      keybox = KeyboxMock.copy(config = w3Config)
    )
    accountService.setActiveAccount(w3Account)

    // And all steps including BuildHardwareDescriptor are complete
    onboardingKeyboxStepStateDao
      .setStateForStep(OnboardingKeyboxStep.DescriptorBackup, Complete)
      .shouldBeOk()
    onboardingKeyboxStepStateDao
      .setStateForStep(OnboardingKeyboxStep.CloudBackup, Complete)
      .shouldBeOk()
    onboardingKeyboxStepStateDao
      .setStateForStep(OnboardingKeyboxStep.NotificationPreferences, Complete)
      .shouldBeOk()
    onboardingKeyboxStepStateDao
      .setStateForStep(OnboardingKeyboxStep.BuildHardwareDescriptor, Complete)
      .shouldBeOk()

    // When getting the pending step
    val pendingStep = service.pendingStep().shouldBeOk()

    // Then null is returned (onboarding complete)
    pendingStep.shouldBe(null)
  }
})
