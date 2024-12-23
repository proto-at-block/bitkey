package build.wallet.statemachine.data.keybox

import build.wallet.account.AccountServiceFake
import build.wallet.account.AccountStatus.NoAccount
import build.wallet.bitkey.factor.PhysicalFactor.App
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.LiteAccountMock
import build.wallet.bitkey.keybox.SoftwareAccountMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.debug.DebugOptionsServiceFake
import build.wallet.recovery.Recovery
import build.wallet.recovery.Recovery.Loading
import build.wallet.recovery.Recovery.NoActiveRecovery
import build.wallet.recovery.RecoverySyncFrequency
import build.wallet.recovery.RecoverySyncerMock
import build.wallet.statemachine.StateMachineMock
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.keybox.AccountData.*
import build.wallet.statemachine.data.recovery.conflict.SomeoneElseIsRecoveringData
import build.wallet.statemachine.data.recovery.conflict.SomeoneElseIsRecoveringDataProps
import build.wallet.statemachine.data.recovery.conflict.SomeoneElseIsRecoveringDataStateMachine
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.time.Duration.Companion.minutes

class AccountDataStateMachineImplTests : FunSpec({

  val accountService = AccountServiceFake()
  val recoverySyncerMock =
    RecoverySyncerMock(
      recovery = Loading,
      turbines::create
    )

  val hasActiveFullAccountDataStateMachine =
    object : HasActiveFullAccountDataStateMachine,
      StateMachineMock<HasActiveFullAccountDataProps, HasActiveFullAccountData>(
        ActiveKeyboxLoadedDataMock
      ) {}
  val hasActiveLiteAccountDataStateMachine =
    object : HasActiveLiteAccountDataStateMachine,
      StateMachineMock<HasActiveLiteAccountDataProps, AccountData>(
        HasActiveLiteAccountDataFake
      ) {}
  val noActiveKeyboxDataStateMachine =
    object : NoActiveAccountDataStateMachine,
      StateMachineMock<NoActiveAccountDataProps, NoActiveAccountData>(
        NoActiveAccountData.CheckingRecoveryOrOnboarding
      ) {}

  val someoneElseIsRecoveringDataStateMachine =
    object : SomeoneElseIsRecoveringDataStateMachine,
      StateMachineMock<SomeoneElseIsRecoveringDataProps, SomeoneElseIsRecoveringData>(
        SomeoneElseIsRecoveringData.ShowingSomeoneElseIsRecoveringData(App, {})
      ) {}
  val debugOptionsService = DebugOptionsServiceFake()

  val stateMachine =
    AccountDataStateMachineImpl(
      hasActiveFullAccountDataStateMachine = hasActiveFullAccountDataStateMachine,
      hasActiveLiteAccountDataStateMachine = hasActiveLiteAccountDataStateMachine,
      noActiveAccountDataStateMachine = noActiveKeyboxDataStateMachine,
      accountService = accountService,
      recoverySyncer = recoverySyncerMock,
      someoneElseIsRecoveringDataStateMachine = someoneElseIsRecoveringDataStateMachine,
      recoverySyncFrequency = RecoverySyncFrequency(1.minutes),
      debugOptionsService = debugOptionsService
    )

  beforeTest {
    accountService.reset()
    hasActiveFullAccountDataStateMachine.reset()
    hasActiveLiteAccountDataStateMachine.reset()
    noActiveKeyboxDataStateMachine.reset()
    debugOptionsService.reset()
  }

  test("no active keybox") {
    accountService.accountState.value = Ok(NoAccount)

    stateMachine.test(Unit) {
      awaitItem().shouldBe(CheckingActiveAccountData)
      recoverySyncerMock.recoveryStatus.value = Ok(NoActiveRecovery)
      awaitItem().shouldBe(NoActiveAccountData.CheckingRecoveryOrOnboarding)
    }
  }

  test("ignores software account") {
    accountService.setActiveAccount(SoftwareAccountMock)

    stateMachine.test(Unit) {
      awaitItem().shouldBe(CheckingActiveAccountData)
    }
  }

  test("has active full account") {
    accountService.setActiveAccount(FullAccountMock)

    stateMachine.test(Unit) {
      awaitItem().shouldBe(CheckingActiveAccountData)

      awaitItem().shouldBe(ActiveKeyboxLoadedDataMock)
    }
  }

  test("has active lite account") {
    accountService.setActiveAccount(LiteAccountMock)

    stateMachine.test(Unit) {
      awaitItem().shouldBe(CheckingActiveAccountData)

      awaitItem().shouldBeInstanceOf<AccountData.HasActiveLiteAccountData>().also {
        it.account.shouldBe(LiteAccountMock)
      }
    }
  }

  test("NoLongerRecoveringData") {
    recoverySyncerMock.recoveryStatus.value = Ok(Recovery.NoLongerRecovering(App))
    accountService.setActiveAccount(FullAccountMock)

    stateMachine.test(Unit) {
      awaitItem().shouldBe(CheckingActiveAccountData)
      awaitItem().shouldBeInstanceOf<AccountData.NoLongerRecoveringFullAccountData>()
    }
  }

  test("SomeoneElseIsRecoveringData") {
    recoverySyncerMock.recoveryStatus.value = Ok(Recovery.SomeoneElseIsRecovering(App))
    accountService.setActiveAccount(FullAccountMock)

    stateMachine.test(Unit) {
      awaitItem().shouldBe(CheckingActiveAccountData)
      val item = awaitItem()
      item.shouldBeInstanceOf<AccountData.SomeoneElseIsRecoveringFullAccountData>()
      val accountData = item.data
      accountData.shouldBeInstanceOf<SomeoneElseIsRecoveringData.ShowingSomeoneElseIsRecoveringData>()
      accountData.cancelingRecoveryLostFactor.shouldBe(App)
    }
  }
})
