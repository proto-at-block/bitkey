package build.wallet.statemachine.inheritance

import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.relationships.InvitationFake
import build.wallet.bitkey.relationships.TrustedContactAlias
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.inheritance.InheritanceServiceMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.core.testWithVirtualTime
import build.wallet.statemachine.recovery.socrec.add.AddingTrustedContactUiProps
import build.wallet.statemachine.recovery.socrec.add.AddingTrustedContactUiStateMachine
import build.wallet.statemachine.ui.awaitBodyMock
import build.wallet.testing.shouldBeOk
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class InviteBeneficiaryUiStateMachineImplTests : FunSpec({
  val inheritanceService = InheritanceServiceMock(
    syncCalls = turbines.create("Sync Calls")
  )

  val inviteBeneficiaryUiStateMachine = InviteBeneficiaryUiStateMachineImpl(
    addingTrustedContactUiStateMachine = object : AddingTrustedContactUiStateMachine,
      ScreenStateMachineMock<AddingTrustedContactUiProps>(
        id = "adding-trusted-contact"
      ) {},
    inheritanceService = inheritanceService
  )

  val props = InviteBeneficiaryUiProps(
    account = FullAccountMock,
    onExit = {},
    onInvited = {}
  )

  test("happy path") {
    inviteBeneficiaryUiStateMachine.testWithVirtualTime(props) {
      awaitBodyMock<AddingTrustedContactUiProps>("adding-trusted-contact") {
        onAddTc(
          TrustedContactAlias("alias"),
          HwFactorProofOfPossession("signed-token")
        ).shouldBeOk().invitation.shouldBe(InvitationFake)
      }
    }
  }
})
