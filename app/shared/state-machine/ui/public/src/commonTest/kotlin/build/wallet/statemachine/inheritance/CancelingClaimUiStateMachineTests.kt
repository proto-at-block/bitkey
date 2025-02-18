package build.wallet.statemachine.inheritance

import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.relationships.RelationshipId
import build.wallet.compose.collections.emptyImmutableList
import build.wallet.compose.collections.immutableListOf
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.inheritance.InheritanceServiceMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.auth.ProofOfPossessionNfcProps
import build.wallet.statemachine.auth.ProofOfPossessionNfcStateMachine
import build.wallet.statemachine.auth.Request.HwKeyProof
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.testWithVirtualTime
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitBodyMock
import build.wallet.statemachine.ui.awaitSheet
import build.wallet.ui.model.StandardClick
import com.github.michaelbull.result.Err
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeTypeOf

class CancelingClaimUiStateMachineTests : FunSpec({
  val inheritanceService = InheritanceServiceMock(
    syncCalls = turbines.create("Sync Calls"),
    cancelClaimCalls = turbines.create("Cancel Claim Calls")
  )
  val stateMachine = CancelingClaimUiStateMachineImpl(
    inheritanceService = inheritanceService,
    proofOfPossessionNfcStateMachine =
      object : ProofOfPossessionNfcStateMachine, ScreenStateMachineMock<ProofOfPossessionNfcProps>(
        id = "pop-nfc"
      ) {}
  )
  val onExitCalls = turbines.create<Unit>("Exit Remove Relationship State Machine")
  val onSuccessCalls = turbines.create<Unit>("Success Remove Relationship State Machine")
  val props = CancelingClaimUiProps(
    relationshipId = RelationshipId("fake-relationship-id"),
    account = FullAccountMock,
    onExit = { onExitCalls.add(Unit) },
    onSuccess = { onSuccessCalls.add(Unit) },
    body = ManagingInheritanceBodyModel(
      onBack = {},
      onLearnMore = {},
      onInviteClick = StandardClick {},
      onTabRowClick = {},
      onAcceptInvitation = {},
      selectedTab = ManagingInheritanceTab.Inheritance,
      hasPendingBeneficiaries = false,
      beneficiaries = BeneficiaryListModel(
        beneficiaries = immutableListOf(),
        onManageClick = {}
      ),
      benefactors = BenefactorListModel(
        benefactors = emptyImmutableList(),
        onManageClick = {}
      )
    )
  )

  test("Successful Cancellation") {
    stateMachine.testWithVirtualTime(props) {
      awaitSheet<DestructiveInheritanceActionBodyModel> {
        isLoading.shouldBeFalse()
        onPrimaryClick()
      }

      awaitBodyMock<ProofOfPossessionNfcProps> {
        request.shouldBeTypeOf<HwKeyProof>().onSuccess(HwFactorProofOfPossession("fake"))
      }

      awaitSheet<DestructiveInheritanceActionBodyModel> {
        this.isLoading.shouldBeTrue()
      }

      inheritanceService.cancelClaimCalls?.awaitItem()
      onSuccessCalls.awaitItem()
    }
  }

  test("Failed Cancellation") {
    inheritanceService.cancelClaimResult = Err(Error("Failed to cancel claim"))

    stateMachine.testWithVirtualTime(props) {
      awaitSheet<DestructiveInheritanceActionBodyModel> {
        isLoading.shouldBeFalse()
        onPrimaryClick()
      }

      awaitBodyMock<ProofOfPossessionNfcProps> {
        request.shouldBeTypeOf<HwKeyProof>().onSuccess(HwFactorProofOfPossession("fake"))
      }

      awaitSheet<DestructiveInheritanceActionBodyModel> {
        this.isLoading.shouldBeTrue()
      }

      inheritanceService.cancelClaimCalls?.awaitItem()
      awaitBody<FormBodyModel> {
        primaryButton.shouldNotBeNull().apply {
          text.shouldContain("Okay")
          onClick()
        }
      }

      onExitCalls.awaitItem()
    }
  }

  test("Exit from initial screen") {
    stateMachine.testWithVirtualTime(props) {
      awaitSheet<DestructiveInheritanceActionBodyModel> {
        onClose()
      }
      onExitCalls.awaitItem()
    }
  }
})
