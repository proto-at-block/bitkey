package build.wallet.statemachine.data.keybox

import build.wallet.account.AccountServiceFake
import build.wallet.account.AccountStatus.NoAccount
import build.wallet.auth.FullAccountAuthKeyRotationServiceMock
import build.wallet.auth.PendingAuthKeyRotationAttempt.ProposedAttempt
import build.wallet.bitkey.auth.AppGlobalAuthPublicKeyMock
import build.wallet.bitkey.factor.PhysicalFactor.App
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.LiteAccountMock
import build.wallet.bitkey.keybox.SoftwareAccountMock
import build.wallet.coroutines.turbine.awaitUntil
import build.wallet.coroutines.turbine.turbines
import build.wallet.debug.DebugOptionsServiceFake
import build.wallet.recovery.Recovery
import build.wallet.recovery.Recovery.NoActiveRecovery
import build.wallet.recovery.RecoverySyncFrequency
import build.wallet.recovery.RecoverySyncerMock
import build.wallet.statemachine.StateMachineMock
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.keybox.AccountData.*
import build.wallet.statemachine.data.keybox.AccountData.HasActiveFullAccountData.ActiveFullAccountLoadedData
import build.wallet.statemachine.data.keybox.AccountData.HasActiveFullAccountData.RotatingAuthKeys
import build.wallet.statemachine.data.keybox.AccountData.NoActiveAccountData.CheckingRecovery
import build.wallet.statemachine.data.recovery.conflict.SomeoneElseIsRecoveringData
import build.wallet.statemachine.data.recovery.conflict.SomeoneElseIsRecoveringData.ShowingSomeoneElseIsRecoveringData
import build.wallet.statemachine.data.recovery.conflict.SomeoneElseIsRecoveringDataProps
import build.wallet.statemachine.data.recovery.conflict.SomeoneElseIsRecoveringDataStateMachine
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryData
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryData.InitiatingLostHardwareRecoveryData.AwaitingNewHardwareData
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryDataStateMachine
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryProps
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeTypeOf
import kotlin.time.Duration.Companion.minutes

class AccountDataStateMachineImplTests : FunSpec({

  val accountService = AccountServiceFake()
  val recoverySyncerMock = RecoverySyncerMock(
    recovery = NoActiveRecovery,
    turbines::create
  )

  val noActiveKeyboxDataStateMachine = object : NoActiveAccountDataStateMachine,
    StateMachineMock<NoActiveAccountDataProps, NoActiveAccountData>(
      CheckingRecovery
    ) {}

  val someoneElseIsRecoveringDataStateMachine = object : SomeoneElseIsRecoveringDataStateMachine,
    StateMachineMock<SomeoneElseIsRecoveringDataProps, SomeoneElseIsRecoveringData>(
      ShowingSomeoneElseIsRecoveringData(App, {})
    ) {}
  val debugOptionsService = DebugOptionsServiceFake()
  val awaitingNewHardwareData = AwaitingNewHardwareData(
    newAppGlobalAuthKey = AppGlobalAuthPublicKeyMock,
    addHardwareKeys = { _, _, _ -> }
  )

  val lostHardwareRecoveryDataStateMachine = object : LostHardwareRecoveryDataStateMachine,
    StateMachineMock<LostHardwareRecoveryProps, LostHardwareRecoveryData>(
      awaitingNewHardwareData
    ) {}

  val fullAccountAuthKeyRotationService = FullAccountAuthKeyRotationServiceMock(turbines::create)

  val stateMachine = AccountDataStateMachineImpl(
    lostHardwareRecoveryDataStateMachine = lostHardwareRecoveryDataStateMachine,
    fullAccountAuthKeyRotationService = fullAccountAuthKeyRotationService,
    noActiveAccountDataStateMachine = noActiveKeyboxDataStateMachine,
    accountService = accountService,
    recoverySyncer = recoverySyncerMock,
    someoneElseIsRecoveringDataStateMachine = someoneElseIsRecoveringDataStateMachine,
    recoverySyncFrequency = RecoverySyncFrequency(1.minutes),
    debugOptionsService = debugOptionsService
  )

  beforeTest {
    accountService.reset()
    noActiveKeyboxDataStateMachine.reset()
    fullAccountAuthKeyRotationService.reset()
    debugOptionsService.reset()
    recoverySyncerMock.reset()
  }

  test("no active keybox") {
    accountService.accountState.value = Ok(NoAccount)

    stateMachine.test(AccountDataProps {}) {
      awaitItem().shouldBe(CheckingActiveAccountData)
      recoverySyncerMock.recoveryStatus.value = Ok(NoActiveRecovery)
      awaitItem().shouldBe(CheckingRecovery)
    }
  }

  test("ignores software account") {
    accountService.setActiveAccount(SoftwareAccountMock)

    stateMachine.test(AccountDataProps {}) {
      awaitItem().shouldBe(CheckingActiveAccountData)
    }
  }

  test("ignores lite account") {
    accountService.setActiveAccount(LiteAccountMock)

    stateMachine.test(AccountDataProps {}) {
      awaitItem().shouldBe(CheckingActiveAccountData)
    }
  }

  test("has active full account") {
    accountService.setActiveAccount(FullAccountMock)

    stateMachine.test(AccountDataProps {}) {
      awaitItem().shouldBe(CheckingActiveAccountData)

      awaitItem().shouldBeTypeOf<ActiveFullAccountLoadedData>()
    }
  }

  test("NoLongerRecoveringData") {
    recoverySyncerMock.recoveryStatus.value = Ok(Recovery.NoLongerRecovering(App))
    accountService.setActiveAccount(FullAccountMock)

    stateMachine.test(AccountDataProps {}) {
      awaitUntil { it is NoLongerRecoveringFullAccountData }
    }
  }

  test("SomeoneElseIsRecoveringData") {
    recoverySyncerMock.recoveryStatus.value = Ok(Recovery.SomeoneElseIsRecovering(App))
    accountService.setActiveAccount(FullAccountMock)

    stateMachine.test(
      AccountDataProps {
      }
    ) {
      awaitItem().shouldBe(CheckingActiveAccountData)
      val item = awaitItem()
      item.shouldBeInstanceOf<SomeoneElseIsRecoveringFullAccountData>()
      val accountData = item.data
      accountData.shouldBeInstanceOf<ShowingSomeoneElseIsRecoveringData>()
      accountData.cancelingRecoveryLostFactor.shouldBe(App)
    }
  }

  test("handle rotate auth keys") {
    accountService.setActiveAccount(FullAccountMock)
    fullAccountAuthKeyRotationService.pendingKeyRotationAttempt.value = ProposedAttempt

    stateMachine.test(AccountDataProps(onLiteAccountCreated = {})) {
      awaitItem().shouldBe(CheckingActiveAccountData)

      awaitUntil<RotatingAuthKeys>().also {
        it.account.shouldBe(FullAccountMock)
        it.pendingAttempt.shouldBe(ProposedAttempt)
      }
    }
  }
})
