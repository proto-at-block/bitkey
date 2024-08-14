package build.wallet.statemachine.data.account.create

import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.keybox.AppDataDeleterMock
import build.wallet.onboarding.OnboardingKeyboxStepState.Complete
import build.wallet.onboarding.OnboardingKeyboxStepStateDaoMock
import build.wallet.statemachine.StateMachineMock
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.account.CreateFullAccountData.*
import build.wallet.statemachine.data.account.create.activate.ActivateFullAccountDataProps
import build.wallet.statemachine.data.account.create.activate.ActivateFullAccountDataStateMachine
import build.wallet.statemachine.data.account.create.keybox.CreateKeyboxDataProps
import build.wallet.statemachine.data.account.create.keybox.CreateKeyboxDataStateMachine
import build.wallet.statemachine.data.account.create.onboard.BackingUpKeyboxToCloudDataMock
import build.wallet.statemachine.data.account.create.onboard.OnboardKeyboxDataProps
import build.wallet.statemachine.data.account.create.onboard.OnboardKeyboxDataStateMachine
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeTypeOf

class CreateFullAccountDataStateMachineImplTests : FunSpec({

  val keyboxDeleter = AppDataDeleterMock(turbines::create)
  val onboardingKeyboxStepStateDao =
    OnboardingKeyboxStepStateDaoMock(
      turbines::create
    )

  val createKeyboxDataStateMachine =
    object : CreateKeyboxDataStateMachine,
      StateMachineMock<CreateKeyboxDataProps, CreateKeyboxData>(
        initialModel = CreateKeyboxData.CreatingAppKeysData(
          rollback = {}
        )
      ) {}
  val onboardKeyboxDataStateMachine =
    object : OnboardKeyboxDataStateMachine,
      StateMachineMock<OnboardKeyboxDataProps, OnboardKeyboxDataFull>(
        initialModel = BackingUpKeyboxToCloudDataMock
      ) {}
  val activateFullAccountDataStateMachine =
    object : ActivateFullAccountDataStateMachine,
      StateMachineMock<ActivateFullAccountDataProps, ActivateKeyboxDataFull>(
        initialModel = ActivateKeyboxDataFull.ActivatingKeyboxDataFull
      ) {}

  val dataStateMachine =
    CreateFullAccountDataStateMachineImpl(
      activateFullAccountDataStateMachine = activateFullAccountDataStateMachine,
      createKeyboxDataStateMachine = createKeyboxDataStateMachine,
      onboardKeyboxDataStateMachine = onboardKeyboxDataStateMachine,
      appDataDeleter = keyboxDeleter,
      onboardingKeyboxStepStateDao = onboardingKeyboxStepStateDao
    )

  val rollbackCalls = turbines.create<Unit>("rollback calls")

  val props = CreateFullAccountDataProps(
    onboardingKeybox = null,
    rollback = { rollbackCalls.add(Unit) },
    context = CreateFullAccountContext.NewFullAccount
  )

  test("data with no existing onboarding") {
    dataStateMachine.test(props) {
      awaitItem().shouldBeTypeOf<CreateKeyboxData.CreatingAppKeysData>()
    }
  }

  test("data with existing onboarding") {
    dataStateMachine.test(props.copy(onboardingKeybox = KeyboxMock)) {
      awaitItem().shouldBeInstanceOf<OnboardKeyboxDataFull>()
    }
  }

  test("happy path with no existing onboarding") {
    dataStateMachine.test(props) {
      awaitItem().shouldBeInstanceOf<CreateKeyboxData>()
      updateProps(props.copy(onboardingKeybox = KeyboxMock))
      awaitItem().shouldBeInstanceOf<OnboardKeyboxDataFull>()
      onboardingKeyboxStepStateDao.cloudBackupStateFlow.emit(Complete)
      onboardingKeyboxStepStateDao.notificationPreferencesStateFlow.emit(Complete)
      awaitItem().shouldBeInstanceOf<ActivateKeyboxDataFull>()
    }
  }

  test("rollback from create keybox") {
    dataStateMachine.test(props) {
      awaitItem().shouldBeInstanceOf<CreateKeyboxData>()
      createKeyboxDataStateMachine.props.rollback()
      rollbackCalls.awaitItem()
    }
  }

  test("rollback from activate keybox") {
    dataStateMachine.test(props) {
      awaitItem().shouldBeInstanceOf<CreateKeyboxData>()
      updateProps(props.copy(onboardingKeybox = KeyboxMock))
      awaitItem().shouldBeInstanceOf<OnboardKeyboxDataFull>()
      onboardingKeyboxStepStateDao.cloudBackupStateFlow.emit(Complete)
      onboardingKeyboxStepStateDao.notificationPreferencesStateFlow.emit(Complete)
      awaitItem().shouldBeInstanceOf<ActivateKeyboxDataFull>()
      activateFullAccountDataStateMachine.props.onDeleteKeyboxAndExitOnboarding()
      keyboxDeleter.deleteCalls.awaitItem()
      rollbackCalls.awaitItem()
    }
  }
})
