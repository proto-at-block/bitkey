package build.wallet.statemachine.data.account.create.activate

import build.wallet.analytics.events.EventTrackerMock
import build.wallet.analytics.events.TrackedAction
import build.wallet.analytics.v1.Action
import build.wallet.analytics.v1.Action.ACTION_APP_GETTINGSTARTED_INITIATED
import build.wallet.bitcoin.BitcoinNetworkType.SIGNET
import build.wallet.bitkey.auth.AppGlobalAuthKeyHwSignatureMock
import build.wallet.bitkey.auth.HwAuthSecp256k1PublicKeyMock
import build.wallet.bitkey.keybox.AppKeyBundleMock
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.onboarding.OnboardingF8eClientMock
import build.wallet.home.GettingStartedTask
import build.wallet.home.GettingStartedTaskDao
import build.wallet.home.GettingStartedTaskDaoMock
import build.wallet.keybox.KeyboxDaoMock
import build.wallet.keybox.keys.OnboardingAppKeyKeystoreFake
import build.wallet.ktor.result.HttpError
import build.wallet.onboarding.OnboardingKeyboxHardwareKeys
import build.wallet.onboarding.OnboardingKeyboxHardwareKeysDaoFake
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

class ActivateFullAccountDataStateMachineImplTests :
  FunSpec({

    val eventTracker = EventTrackerMock(turbines::create)
    val gettingStartedTaskDao = GettingStartedTaskDaoMock(turbines::create)
    val keyboxDao = KeyboxDaoMock(turbines::create)
    val onboardingKeyboxStepStateDao =
      OnboardingKeyboxStepStateDaoMock(turbines::create)
    val onboardingF8eClient = OnboardingF8eClientMock(turbines::create)
    val onboardingAppKeyKeystore = OnboardingAppKeyKeystoreFake()
    val onboardingKeyboxHwAuthPublicKeyDao = OnboardingKeyboxHardwareKeysDaoFake()

    val dataStateMachine =
      ActivateFullAccountDataStateMachineImpl(
        eventTracker = eventTracker,
        gettingStartedTaskDao = gettingStartedTaskDao,
        keyboxDao = keyboxDao,
        onboardingKeyboxStepStateDao = onboardingKeyboxStepStateDao,
        onboardingF8eClient = onboardingF8eClient,
        onboardingAppKeyKeystore = onboardingAppKeyKeystore,
        onboardingKeyboxHardwareKeysDao = onboardingKeyboxHwAuthPublicKeyDao
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
      onboardingF8eClient.reset()
      onboardingAppKeyKeystore.persistAppKeys(
        spendingKey = AppKeyBundleMock.spendingKey,
        globalAuthKey = AppKeyBundleMock.authKey,
        recoveryAuthKey = AppKeyBundleMock.recoveryAuthKey,
        bitcoinNetworkType = SIGNET
      )
      onboardingKeyboxHwAuthPublicKeyDao.set(
        OnboardingKeyboxHardwareKeys(HwAuthSecp256k1PublicKeyMock, AppGlobalAuthKeyHwSignatureMock)
      )
    }

    test("activate new keybox successfully") {
      dataStateMachine.test(props) {
        awaitItem().let {
          it.shouldBeTypeOf<ActivatingKeyboxDataFull>()
        }

        onboardingAppKeyKeystore.appKeys.shouldBeNull()
        onboardingKeyboxHwAuthPublicKeyDao.keys.shouldBeNull()

        // Activating wallet and adding tasks
        onboardingF8eClient.completeOnboardingCalls.awaitItem()

        gettingStartedTaskDao.expectOnboardingTasks()
        eventTracker.expectOnboardingEvents()
        onboardingKeyboxStepStateDao.clearCalls.awaitItem()
      }
    }

    test("complete onboarding error and retry") {
      onboardingF8eClient.completeOnboardingResult = Err(HttpError.NetworkError(Throwable()))
      dataStateMachine.test(props) {
        awaitItem().let {
          it.shouldBeTypeOf<ActivatingKeyboxDataFull>()
        }

        onboardingAppKeyKeystore.appKeys.shouldBeNull()
        onboardingKeyboxHwAuthPublicKeyDao.keys.shouldBeNull()

        onboardingF8eClient.completeOnboardingCalls.awaitItem()

        awaitItem().let {
          it.shouldBeTypeOf<FailedToActivateKeyboxDataFull>()
          onboardingF8eClient.completeOnboardingResult = Ok(Unit)
          it.retry()
        }

        awaitItem().let {
          it.shouldBeTypeOf<ActivatingKeyboxDataFull>()
        }
        onboardingF8eClient.completeOnboardingCalls.awaitItem()

        gettingStartedTaskDao.expectOnboardingTasks()
        eventTracker.expectOnboardingEvents()
        onboardingKeyboxStepStateDao.clearCalls.awaitItem()
      }
    }
  })

private suspend fun GettingStartedTaskDao.expectOnboardingTasks() {
  getTasks().shouldContainExactly(
    GettingStartedTask(
      GettingStartedTask.TaskId.AddBitcoin,
      GettingStartedTask.TaskState.Incomplete
    ),
    GettingStartedTask(
      GettingStartedTask.TaskId.InviteTrustedContact,
      GettingStartedTask.TaskState.Incomplete
    ),
    GettingStartedTask(
      GettingStartedTask.TaskId.EnableSpendingLimit,
      GettingStartedTask.TaskState.Incomplete
    ),
    GettingStartedTask(
      GettingStartedTask.TaskId.AddAdditionalFingerprint,
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
