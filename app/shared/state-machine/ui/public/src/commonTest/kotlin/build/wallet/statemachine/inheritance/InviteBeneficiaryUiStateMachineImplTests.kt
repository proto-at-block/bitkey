package build.wallet.statemachine.inheritance

import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.relationships.TrustedContactAlias
import build.wallet.bitkey.socrec.InvitationFake
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.inheritance.InheritanceServiceFake
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.core.awaitScreenWithBodyModelMock
import build.wallet.statemachine.core.test
import build.wallet.statemachine.recovery.socrec.add.AddingTrustedContactUiProps
import build.wallet.statemachine.recovery.socrec.add.AddingTrustedContactUiStateMachine
import build.wallet.testing.shouldBeOk
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class InviteBeneficiaryUiStateMachineImplTests : FunSpec({
  val inheritanceService = InheritanceServiceFake()

  val inviteBeneficiaryUiStateMachine = InviteBeneficiaryUiStateMachineImpl(
    addingTrustedContactUiStateMachine = object : AddingTrustedContactUiStateMachine,
      ScreenStateMachineMock<AddingTrustedContactUiProps>(
        id = "adding-trusted-contact"
      ) {},
    inheritanceService = inheritanceService
  )

  val props = InviteBeneficiaryUiProps(
    account = FullAccountMock,
    onExit = {}
  )

  test("happy path") {
    inviteBeneficiaryUiStateMachine.test(props) {
      awaitScreenWithBodyModelMock<AddingTrustedContactUiProps>("adding-trusted-contact") {
        onAddTc(
          TrustedContactAlias("alias"),
          HwFactorProofOfPossession("signed-token")
        ).shouldBeOk().invitation.shouldBe(InvitationFake)
      }
    }
  }
})
