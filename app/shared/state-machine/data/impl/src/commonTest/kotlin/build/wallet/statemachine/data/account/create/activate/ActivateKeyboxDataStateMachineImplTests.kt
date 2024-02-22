package build.wallet.statemachine.data.account.create.activate

import build.wallet.analytics.events.EventTrackerMock
import build.wallet.analytics.events.TrackedAction
import build.wallet.analytics.v1.Action
import build.wallet.analytics.v1.Action.ACTION_APP_GETTINGSTARTED_INITIATED
import build.wallet.bitcoin.BitcoinNetworkType.SIGNET
import build.wallet.bitkey.auth.HwAuthSecp256k1PublicKeyMock
import build.wallet.bitkey.keybox.AppKeyBundleMock
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.onboarding.OnboardingServiceMock
import build.wallet.home.GettingStartedTask
import build.wallet.home.GettingStartedTaskDao
import build.wallet.home.GettingStartedTaskDaoMock
import build.wallet.keybox.KeyboxDaoMock
import build.wallet.keybox.keys.OnboardingAppKeyKeystoreFake
import build.wallet.ktor.result.HttpError
import build.wallet.onboarding.OnboardingKeyboxHwAuthPublicKeyDaoFake
import build.wallet.onboarding.OnboardingKeyboxStepStateDaoMock
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.account.CreateFullAccountData.ActivateKeyboxDataFull.ActivatingKeyboxDataFull
import build.wallet.statemachine.data.account.CreateFullAccountData.ActivateKeyboxDataFull.FailedToActivateKeyboxDataFull
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf

class ActivateKeyboxDataStateMachineImplTests : FunSpec({

  val eventTracker = EventTrackerMock(turbines::create)
  val gettingStartedTaskDao = GettingStartedTaskDaoMock(turbines::create)
  val keyboxDao = KeyboxDaoMock(turbines::create)
  val onboardingKeyboxStepStateDao =
    OnboardingKeyboxStepStateDaoMock(turbines::create)
  val onboardingService = OnboardingServiceMock(turbines::create)
  val onboardingAppKeyKeystore = OnboardingAppKeyKeystoreFake()
  val onboardingKeyboxHwAuthPublicKeyDao = OnboardingKeyboxHwAuthPublicKeyDaoFake()

  val dataStateMachine =
    ActivateFullAccountDataStateMachineImpl(
      eventTracker = eventTracker,
      gettingStartedTaskDao = gettingStartedTaskDao,
      keyboxDao = keyboxDao,
      onboardingKeyboxStepStateDao = onboardingKeyboxStepStateDao,
      onboardingService = onboardingService,
      onboardingAppKeyKeystore = onboardingAppKeyKeystore,
      onboardingKeyboxHwAuthPublicKeyDao = onboardingKeyboxHwAuthPublicKeyDao
    )

  val exitOnboardingCalls = turbines.create<Unit>("exitOnboarding calls")

  val props =
    ActivateFullAccountDataProps(
      keybox = KeyboxMock,
      onDeleteKeyboxAndExitOnboarding = { exitOnboardingCalls.add(Unit) }
    )

  beforeTest {
    gettingStartedTaskDao.reset()
    onboardingKeyboxStepStateDao.reset()
    keyboxDao.reset()
    onboardingService.reset()
    onboardingAppKeyKeystore.persistAppKeys(
      spendingKey = AppKeyBundleMock.spendingKey,
      globalAuthKey = AppKeyBundleMock.authKey,
      recoveryAuthKey = AppKeyBundleMock.recoveryAuthKey!!,
      bitcoinNetworkType = SIGNET
    )
    onboardingKeyboxHwAuthPublicKeyDao.set(HwAuthSecp256k1PublicKeyMock)
  }

  test("activate new keybox successfully") {
    dataStateMachine.test(props) {
      awaitItem().let {
        it.shouldBeTypeOf<ActivatingKeyboxDataFull>()
      }

      onboardingAppKeyKeystore.appKeys.shouldBeNull()
      onboardingKeyboxHwAuthPublicKeyDao.hwAuthPublicKey.shouldBeNull()

      // Activating wallet and adding tasks
      onboardingService.completeOnboardingCalls.awaitItem()

      gettingStartedTaskDao.expectOnboardingTasks()
      eventTracker.expectOnboardingEvents()
      onboardingKeyboxStepStateDao.clearCalls.awaitItem()
    }
  }

  test("complete onboarding error and retry") {
    onboardingService.completeOnboardingResult = Err(HttpError.NetworkError(Throwable()))
    dataStateMachine.test(props) {
      awaitItem().let {
        it.shouldBeTypeOf<ActivatingKeyboxDataFull>()
      }

      onboardingAppKeyKeystore.appKeys.shouldBeNull()
      onboardingKeyboxHwAuthPublicKeyDao.hwAuthPublicKey.shouldBeNull()

      onboardingService.completeOnboardingCalls.awaitItem()

      awaitItem().let {
        it.shouldBeTypeOf<FailedToActivateKeyboxDataFull>()
        onboardingService.completeOnboardingResult = Ok(Unit)
        it.retry()
      }

      awaitItem().let {
        it.shouldBeTypeOf<ActivatingKeyboxDataFull>()
      }
      onboardingService.completeOnboardingCalls.awaitItem()

      gettingStartedTaskDao.expectOnboardingTasks()
      eventTracker.expectOnboardingEvents()
      onboardingKeyboxStepStateDao.clearCalls.awaitItem()
    }
  }
})

private suspend fun GettingStartedTaskDao.expectOnboardingTasks() {
  getTasks().shouldContainExactly(
    GettingStartedTask(
      GettingStartedTask.TaskId.EnableSpendingLimit,
      GettingStartedTask.TaskState.Incomplete
    ),
    GettingStartedTask(
      GettingStartedTask.TaskId.InviteTrustedContact,
      GettingStartedTask.TaskState.Incomplete
    ),
    GettingStartedTask(
      GettingStartedTask.TaskId.AddBitcoin,
      GettingStartedTask.TaskState.Incomplete
    )
  )
}

private suspend fun EventTrackerMock.expectOnboardingEvents() {
  eventCalls.awaitItem().shouldBe(
    TrackedAction(ACTION_APP_GETTINGSTARTED_INITIATED)
  )
  eventCalls.awaitItem().shouldBe(
    TrackedAction(Action.ACTION_APP_ACCOUNT_CREATED)
  )
}
