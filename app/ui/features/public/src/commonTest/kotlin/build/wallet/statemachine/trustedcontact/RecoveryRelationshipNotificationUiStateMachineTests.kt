package build.wallet.statemachine.trustedcontact

import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId.*
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.relationships.EndorsedBeneficiaryFake
import build.wallet.bitkey.relationships.EndorsedTrustedContactFake1
import build.wallet.bitkey.relationships.RelationshipId
import build.wallet.bitkey.relationships.UnendorsedBeneficiaryFake
import build.wallet.bitkey.relationships.UnendorsedTrustedContactFake
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.relationships.RelationshipsFake
import build.wallet.inheritance.InheritanceServiceMock
import build.wallet.recovery.socrec.SocRecServiceFake
import build.wallet.statemachine.core.SuccessBodyModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.robots.awaitLoadingScreen
import io.kotest.core.spec.style.FunSpec

class RecoveryRelationshipNotificationUiStateMachineTests : FunSpec({
  val socRecService = SocRecServiceFake()
  val inheritanceService = InheritanceServiceMock(
    syncCalls = turbines.create("Sync Calls")
  )
  val stateMachine = RecoveryRelationshipNotificationUiStateMachineImpl(
    socRecService = socRecService,
    inheritanceService = inheritanceService
  )
  val backCalls = turbines.create<Unit>("Back Calls")

  beforeTest {
    socRecService.reset()
    inheritanceService.reset()
  }

  test("Recovery Contact accepts invite") {
    socRecService.socRecRelationships.value = RelationshipsFake.copy(
      unendorsedTrustedContacts = listOf(
        UnendorsedTrustedContactFake.copy(
          relationshipId = "accepted-invite-test"
        )
      )
    )
    stateMachine.test(
      props = RecoveryRelationshipNotificationUiProps(
        fullAccount = FullAccountMock,
        action = RecoveryRelationshipNotificationAction.ProtectedCustomerInviteAccepted,
        recoveryRelationshipId = RelationshipId("accepted-invite-test"),
        onBack = { backCalls.add(Unit) }
      )
    ) {
      awaitLoadingScreen(PROTECTED_CUSTOMER_RECOVERY_RELATIONSHIP_LOADING)
      awaitLoadingScreen(PROTECTED_CUSTOMER_RECOVERY_RELATIONSHIP_AWAITING_ENDORSEMENT)

      // Complete endorsement:
      socRecService.socRecRelationships.value = RelationshipsFake.copy(
        endorsedTrustedContacts = listOf(
          EndorsedTrustedContactFake1.copy(
            relationshipId = "accepted-invite-test"
          )
        )
      )
      awaitBody<SuccessBodyModel>(PROTECTED_CUSTOMER_INVITE_ACCEPTED)
    }
  }

  test("Recovery contact not found") {
    socRecService.socRecRelationships.value = RelationshipsFake.copy(
      endorsedTrustedContacts = listOf(
        EndorsedTrustedContactFake1.copy(
          relationshipId = "accepted-invite-test"
        )
      )
    )
    stateMachine.test(
      props = RecoveryRelationshipNotificationUiProps(
        fullAccount = FullAccountMock,
        action = RecoveryRelationshipNotificationAction.ProtectedCustomerInviteAccepted,
        recoveryRelationshipId = RelationshipId("invalid-id"),
        onBack = { backCalls.add(Unit) }
      )
    ) {
      awaitLoadingScreen(PROTECTED_CUSTOMER_RECOVERY_RELATIONSHIP_LOADING)
      awaitBody<FormBodyModel>(PROTECTED_CUSTOMER_RECOVERY_RELATIONSHIP_NOT_ACTIVE)
    }
  }

  test("Beneficiary accepts invite") {
    inheritanceService.relationships.value = RelationshipsFake.copy(
      unendorsedTrustedContacts = listOf(
        UnendorsedBeneficiaryFake.copy(
          relationshipId = "accepted-invite-test"
        )
      )
    )
    stateMachine.test(
      props = RecoveryRelationshipNotificationUiProps(
        fullAccount = FullAccountMock,
        action = RecoveryRelationshipNotificationAction.BenefactorInviteAccepted,
        recoveryRelationshipId = RelationshipId("accepted-invite-test"),
        onBack = { backCalls.add(Unit) }
      )
    ) {
      awaitLoadingScreen(BENEFACTOR_RECOVERY_RELATIONSHIP_LOADING)
      awaitLoadingScreen(BENEFACTOR_RECOVERY_RELATIONSHIP_AWAITING_ENDORSEMENT)

      // Endorse beneficiary:
      inheritanceService.relationships.value = RelationshipsFake.copy(
        endorsedTrustedContacts = listOf(
          EndorsedBeneficiaryFake.copy(
            relationshipId = "accepted-invite-test"
          )
        )
      )

      awaitBody<SuccessBodyModel>(BENEFACTOR_INVITE_ACCEPTED)
    }
  }

  test("Beneficiary not found") {
    inheritanceService.relationships.value = RelationshipsFake.copy(
      endorsedTrustedContacts = listOf(
        EndorsedBeneficiaryFake.copy(
          relationshipId = "accepted-invite-test"
        )
      )
    )
    stateMachine.test(
      props = RecoveryRelationshipNotificationUiProps(
        fullAccount = FullAccountMock,
        action = RecoveryRelationshipNotificationAction.BenefactorInviteAccepted,
        recoveryRelationshipId = RelationshipId("invalid-id"),
        onBack = { backCalls.add(Unit) }
      )
    ) {
      awaitLoadingScreen(BENEFACTOR_RECOVERY_RELATIONSHIP_LOADING)
      awaitBody<FormBodyModel>(BENEFACTOR_RECOVERY_RELATIONSHIP_NOT_ACTIVE)
    }
  }
})
