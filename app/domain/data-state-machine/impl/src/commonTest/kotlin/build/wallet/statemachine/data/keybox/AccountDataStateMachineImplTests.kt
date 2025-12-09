package build.wallet.statemachine.data.keybox

import build.wallet.auth.FullAccountAuthKeyRotationServiceMock
import build.wallet.auth.PendingAuthKeyRotationAttempt.ProposedAttempt
import build.wallet.bitkey.factor.PhysicalFactor.App
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.coroutines.turbine.awaitUntil
import build.wallet.coroutines.turbine.turbines
import build.wallet.recovery.Recovery
import build.wallet.recovery.Recovery.NoActiveRecovery
import build.wallet.recovery.RecoveryStatusServiceMock
import build.wallet.statemachine.StateMachineMock
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.keybox.AccountData.*
import build.wallet.statemachine.data.keybox.AccountData.HasActiveFullAccountData.ActiveFullAccountLoadedData
import build.wallet.statemachine.data.keybox.AccountData.HasActiveFullAccountData.RotatingAuthKeys
import build.wallet.statemachine.data.recovery.conflict.SomeoneElseIsRecoveringData
import build.wallet.statemachine.data.recovery.conflict.SomeoneElseIsRecoveringData.ShowingSomeoneElseIsRecoveringData
import build.wallet.statemachine.data.recovery.conflict.SomeoneElseIsRecoveringDataProps
import build.wallet.statemachine.data.recovery.conflict.SomeoneElseIsRecoveringDataStateMachine
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeTypeOf

class AccountDataStateMachineImplTests : FunSpec({

  val recoveryStatusServiceMock = RecoveryStatusServiceMock(
    recovery = NoActiveRecovery,
    turbines::create
  )

  val someoneElseIsRecoveringDataStateMachine = object : SomeoneElseIsRecoveringDataStateMachine,
    StateMachineMock<SomeoneElseIsRecoveringDataProps, SomeoneElseIsRecoveringData>(
      ShowingSomeoneElseIsRecoveringData(App, {})
    ) {}

  val fullAccountAuthKeyRotationService = FullAccountAuthKeyRotationServiceMock(turbines::create)

  val stateMachine = AccountDataStateMachineImpl(
    fullAccountAuthKeyRotationService = fullAccountAuthKeyRotationService,
    recoveryStatusService = recoveryStatusServiceMock,
    someoneElseIsRecoveringDataStateMachine = someoneElseIsRecoveringDataStateMachine
  )

  beforeTest {
    fullAccountAuthKeyRotationService.reset()
    recoveryStatusServiceMock.reset()
  }

  test("has active full account with no recovery") {
    stateMachine.test(AccountDataProps(account = FullAccountMock)) {
      awaitItem().shouldBeTypeOf<ActiveFullAccountLoadedData>().also {
        it.account.shouldBe(FullAccountMock)
      }
    }
  }

  test("shows NoLongerRecoveringData when recovery was canceled") {
    recoveryStatusServiceMock.recoveryStatus.value = Recovery.NoLongerRecovering(App)

    stateMachine.test(AccountDataProps(account = FullAccountMock)) {
      awaitUntil { it is NoLongerRecoveringFullAccountData }.also {
        it.shouldBeTypeOf<NoLongerRecoveringFullAccountData>()
        it.canceledRecoveryLostFactor.shouldBe(App)
      }
    }
  }

  test("shows SomeoneElseIsRecoveringData when another device is recovering") {
    recoveryStatusServiceMock.recoveryStatus.value = Recovery.SomeoneElseIsRecovering(App)

    stateMachine.test(AccountDataProps(account = FullAccountMock)) {
      val item = awaitItem()
      item.shouldBeInstanceOf<SomeoneElseIsRecoveringFullAccountData>()
      item.fullAccountId.shouldBe(FullAccountMock.accountId)

      val accountData = item.data
      accountData.shouldBeInstanceOf<ShowingSomeoneElseIsRecoveringData>()
      accountData.cancelingRecoveryLostFactor.shouldBe(App)
    }
  }

  test("shows CheckingActiveAccountData when recovery is loading") {
    recoveryStatusServiceMock.recoveryStatus.value = Recovery.Loading

    stateMachine.test(AccountDataProps(account = FullAccountMock)) {
      awaitItem().shouldBe(CheckingActiveAccountData)
    }
  }

  test("handles rotate auth keys") {
    fullAccountAuthKeyRotationService.pendingKeyRotationAttempt.value = ProposedAttempt

    stateMachine.test(AccountDataProps(account = FullAccountMock)) {
      awaitUntil<RotatingAuthKeys>().also {
        it.account.shouldBe(FullAccountMock)
        it.pendingAttempt.shouldBe(ProposedAttempt)
      }
    }
  }

  test("combines rotate auth keys with NoLongerRecovering recovery state") {
    recoveryStatusServiceMock.recoveryStatus.value = Recovery.NoLongerRecovering(App)
    fullAccountAuthKeyRotationService.pendingKeyRotationAttempt.value = ProposedAttempt

    stateMachine.test(AccountDataProps(account = FullAccountMock)) {
      // Recovery state takes precedence
      awaitUntil { it is NoLongerRecoveringFullAccountData }
    }
  }
})
