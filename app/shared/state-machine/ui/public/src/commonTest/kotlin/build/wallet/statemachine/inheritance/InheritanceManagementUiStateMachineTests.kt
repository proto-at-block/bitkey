package build.wallet.statemachine.inheritance

import build.wallet.bitkey.inheritance.BenefactorLockedClaimFake
import build.wallet.bitkey.inheritance.BeneficiaryLockedClaimFake
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.relationships.EndorsedTrustedContactFake1
import build.wallet.bitkey.relationships.ProtectedCustomerFake
import build.wallet.coachmark.CoachmarkIdentifier.InheritanceCoachmark
import build.wallet.coachmark.CoachmarkServiceMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.inheritance.ContactClaimState
import build.wallet.inheritance.InheritanceServiceMock
import build.wallet.platform.web.InAppBrowserNavigatorMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.StateMachineMock
import build.wallet.statemachine.core.test
import build.wallet.statemachine.inheritance.claims.complete.CompleteInheritanceClaimUiStateMachine
import build.wallet.statemachine.inheritance.claims.complete.CompleteInheritanceClaimUiStateMachineProps
import build.wallet.statemachine.inheritance.claims.start.StartClaimUiStateMachine
import build.wallet.statemachine.inheritance.claims.start.StartClaimUiStateMachineProps
import build.wallet.statemachine.moneyhome.card.CardModel
import build.wallet.statemachine.moneyhome.card.inheritance.InheritanceCardUiProps
import build.wallet.statemachine.moneyhome.card.inheritance.InheritanceCardUiStateMachine
import build.wallet.statemachine.send.SendUiProps
import build.wallet.statemachine.send.SendUiStateMachine
import build.wallet.statemachine.trustedcontact.TrustedContactEnrollmentUiProps
import build.wallet.statemachine.trustedcontact.TrustedContactEnrollmentUiStateMachine
import build.wallet.statemachine.trustedcontact.reinvite.ReinviteTrustedContactUiProps
import build.wallet.statemachine.trustedcontact.reinvite.ReinviteTrustedContactUiStateMachine
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitBodyMock
import build.wallet.statemachine.ui.awaitSheet
import build.wallet.ui.model.list.ListItemAccessory
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.datetime.Instant

class InheritanceManagementUiStateMachineTests : FunSpec({

  val inheritanceService = InheritanceServiceMock(turbines.create("sync-calls"))
  val coachmarkService = CoachmarkServiceMock(emptyList(), turbines::create)

  val stateMachine = InheritanceManagementUiStateMachineImpl(
    inviteBeneficiaryUiStateMachine = object : InviteBeneficiaryUiStateMachine,
      ScreenStateMachineMock<InviteBeneficiaryUiProps>(id = "invite-beneficiary") {},
    trustedContactEnrollmentUiStateMachine = object : TrustedContactEnrollmentUiStateMachine,
      ScreenStateMachineMock<TrustedContactEnrollmentUiProps>(id = "trusted-contact-enrollment") {},
    cancelingClaimUiStateMachine = object : CancelingClaimUiStateMachine,
      ScreenStateMachineMock<CancelingClaimUiProps>(id = "canceling-claim") {},
    removingRelationshipUiStateMachine = object : RemovingRelationshipUiStateMachine,
      ScreenStateMachineMock<RemovingRelationshipUiProps>(id = "removing-relationship") {},
    inheritanceService = inheritanceService,
    startClaimUiStateMachine = object : StartClaimUiStateMachine,
      ScreenStateMachineMock<StartClaimUiStateMachineProps>(id = "start-claim") {},
    completeClaimUiStateMachine = object : CompleteInheritanceClaimUiStateMachine,
      ScreenStateMachineMock<CompleteInheritanceClaimUiStateMachineProps>(id = "complete-claim") {},
    reinviteTrustedContactUiStateMachine = object : ReinviteTrustedContactUiStateMachine,
      ScreenStateMachineMock<ReinviteTrustedContactUiProps>(id = "reinvite") {},
    declineInheritanceClaimUiStateMachine = object : DeclineInheritanceClaimUiStateMachine,
      ScreenStateMachineMock<DeclineInheritanceClaimUiProps>(id = "decline-claim") {},
    inAppBrowserNavigator = InAppBrowserNavigatorMock(turbines::create),
    inheritanceCardUiStateMachine = object : InheritanceCardUiStateMachine,
      StateMachineMock<InheritanceCardUiProps, List<CardModel>>(
        initialModel = emptyList()
      ) {},
    coachmarkService = coachmarkService,
    sendUiStateMachine = object : SendUiStateMachine,
      ScreenStateMachineMock<SendUiProps>("send") {}
  )

  val props = InheritanceManagementUiProps(
    account = FullAccountMock,
    selectedTab = ManagingInheritanceTab.Inheritance,
    onBack = {},
    onGoToUtxoConsolidation = {}
  )

  beforeTest {
    coachmarkService.resetCoachmarks()
    inheritanceService.reset()
  }

  test("marks coachmark as displayed") {
    coachmarkService.defaultCoachmarks = listOf(InheritanceCoachmark)
    stateMachine.test(props) {
      awaitBody<ManagingInheritanceBodyModel> {}
      coachmarkService.markDisplayedTurbine.awaitItem().shouldBe(InheritanceCoachmark)
    }
  }

  test("removing a benefactor w/ an approved claim") {
    inheritanceService.benefactorClaimState.value = inheritanceService.benefactorClaimState.value
      .toMutableList()
      .apply {
        add(
          ContactClaimState.Benefactor(
            timestamp = Instant.DISTANT_PAST,
            relationship = ProtectedCustomerFake,
            claims = listOf(BeneficiaryLockedClaimFake)
          )
        )
      }
      .toImmutableList()

    stateMachine.test(props) {
      // Loading claims
      awaitBody<ManagingInheritanceBodyModel> {
        benefactors.items.size.shouldBe(0)
      }

      // claims loaded
      awaitBody<ManagingInheritanceBodyModel> {
        benefactors.items.size.shouldBe(1)

        benefactors.items[0].trailingAccessory
          .shouldBeTypeOf<ListItemAccessory.ButtonAccessory>()
          .model
          .onClick()
      }

      awaitSheet<ManageInheritanceContactBodyModel> {
        onRemove()
      }

      awaitSheet<BeneficiaryApprovedClaimWarningBodyModel> {
        onTransferFunds()
      }

      awaitBodyMock<CompleteInheritanceClaimUiStateMachineProps>("complete-claim")
    }
  }

  test("removing a beneficiary w/ an approved claim") {
    inheritanceService.beneficiaryClaimState.value = inheritanceService.beneficiaryClaimState.value
      .toMutableList()
      .apply {
        add(
          ContactClaimState.Beneficiary(
            timestamp = Instant.DISTANT_PAST,
            relationship = EndorsedTrustedContactFake1,
            claims = listOf(BenefactorLockedClaimFake),
            isInvite = false
          )
        )
      }
      .toImmutableList()

    stateMachine.test(props) {
      // Loading claims
      awaitBody<ManagingInheritanceBodyModel> {
        beneficiaries.items.size.shouldBe(0)
      }

      // claims loaded
      awaitBody<ManagingInheritanceBodyModel> {
        beneficiaries.items.size.shouldBe(1)

        beneficiaries.items[0].trailingAccessory
          .shouldBeTypeOf<ListItemAccessory.ButtonAccessory>()
          .model
          .onClick()
      }

      awaitSheet<ManageInheritanceContactBodyModel> {
        onRemove()
      }

      awaitSheet<BenefactorApprovedClaimWarningBodyModel> {
        onTransferFunds()
      }

      awaitBodyMock<SendUiProps>("send")
    }
  }
})
