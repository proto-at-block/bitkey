package build.wallet.statemachine.data.app

import build.wallet.coroutines.turbine.turbines
import build.wallet.debug.DebugOptionsServiceFake
import build.wallet.feature.FeatureFlagServiceFake
import build.wallet.money.currency.FiatCurrencyRepositoryMock
import build.wallet.platform.permissions.PermissionCheckerMock
import build.wallet.statemachine.StateMachineMock
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.app.AppData.AppLoadedData
import build.wallet.statemachine.data.app.AppData.LoadingAppData
import build.wallet.statemachine.data.keybox.AccountData
import build.wallet.statemachine.data.keybox.AccountData.CheckingActiveAccountData
import build.wallet.statemachine.data.keybox.AccountDataStateMachine
import build.wallet.statemachine.data.keybox.ActiveKeyboxLoadedDataMock
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class AppDataStateMachineImplTests : FunSpec({

  val accountDataStateMachine =
    object : AccountDataStateMachine, StateMachineMock<Unit, AccountData>(
      initialModel = CheckingActiveAccountData
    ) {}
  val permissionChecker = PermissionCheckerMock()
  val fiatCurrencyRepository = FiatCurrencyRepositoryMock(turbines::create)
  val debugOptionsService = DebugOptionsServiceFake()
  val featureFlagService = FeatureFlagServiceFake()

  val stateMachine = AppDataStateMachineImpl(
    featureFlagService = featureFlagService,
    accountDataStateMachine = accountDataStateMachine,
    fiatCurrencyRepository = fiatCurrencyRepository,
    debugOptionsService = debugOptionsService
  )

  suspend fun shouldLaunchRepositories() {
    fiatCurrencyRepository.updateFromServerCalls.awaitItem().shouldBe(Unit)
  }

  suspend fun shouldRunInitialSideEffects() {
    shouldLaunchRepositories()
    featureFlagService.featureFlagsInitialized.value = true
  }

  val accountData = ActiveKeyboxLoadedDataMock

  beforeTest {
    featureFlagService.reset()
    debugOptionsService.reset()
  }

  test("load app") {
    permissionChecker.permissionsOn = true

    stateMachine.test(props = Unit) {
      // Initial app loading data
      awaitItem().shouldBe(LoadingAppData)

      shouldRunInitialSideEffects()

      // App data updated, loading keybox
      awaitItem().shouldBe(
        AppLoadedData(
          accountData = CheckingActiveAccountData
        )
      )

      // Update child keybox data state machine
      accountDataStateMachine.emitModel(accountData)

      // App data updated, keybox loaded
      awaitItem().shouldBe(
        AppLoadedData(
          accountData = accountData
        )
      )
    }
  }
})
