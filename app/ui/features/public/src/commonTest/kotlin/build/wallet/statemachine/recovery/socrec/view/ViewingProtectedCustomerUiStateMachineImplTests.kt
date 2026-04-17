package build.wallet.statemachine.recovery.socrec.view

import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.LiteAccountMock
import build.wallet.bitkey.relationships.ProtectedCustomerFake
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.auth.PrivilegedActionProof
import build.wallet.relationships.RelationshipsServiceMock
import build.wallet.statemachine.BodyModelMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.auth.ActionProofType
import build.wallet.statemachine.auth.HardwareAuthUiProps
import build.wallet.statemachine.auth.HardwareAuthUiStateMachine
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.statemachine.ui.awaitUntilBodyMock
import build.wallet.statemachine.ui.awaitUntilSheet
import build.wallet.time.ClockFake
import build.wallet.ui.model.alert.ButtonAlertModel
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf

class ViewingProtectedCustomerUiStateMachineImplTests : FunSpec({
  val clock = ClockFake()
  val relationshipsService = RelationshipsServiceMock(turbines::create, clock)
  val hardwareAuthStateMachine =
    object : HardwareAuthUiStateMachine, ScreenStateMachineMock<HardwareAuthUiProps>(
      id = "hardware-auth"
    ) {}

  val stateMachine = ViewingProtectedCustomerUiStateMachineImpl(
    relationshipsService = relationshipsService,
    hardwareAuthUiStateMachine = hardwareAuthStateMachine
  )

  val onExitCalls = turbines.create<Unit>("onExit calls")
  val onHelpWithRecoveryCalls = turbines.create<Unit>("onHelpWithRecovery calls")

  val screenModel = BodyModelMock(id = "base-screen", latestProps = Unit).asRootScreen()

  val liteAccountProps = ViewingProtectedCustomerProps(
    account = LiteAccountMock,
    screenModel = screenModel,
    protectedCustomer = ProtectedCustomerFake,
    onHelpWithRecovery = { onHelpWithRecoveryCalls.add(Unit) },
    onExit = { onExitCalls.add(Unit) }
  )

  val fullAccountProps = ViewingProtectedCustomerProps(
    account = FullAccountMock,
    screenModel = screenModel,
    protectedCustomer = ProtectedCustomerFake,
    onHelpWithRecovery = { onHelpWithRecoveryCalls.add(Unit) },
    onExit = { onExitCalls.add(Unit) }
  )

  test("Lite Account TC removes self without hardware auth") {
    stateMachine.test(liteAccountProps) {
      // Initial sheet showing protected customer details
      awaitUntilSheet<FormBodyModel> {
        secondaryButton.shouldNotBeNull().onClick()
      }

      // Alert confirmation dialog appears
      awaitItem().alertModel.shouldBeTypeOf<ButtonAlertModel>().onPrimaryButtonClick()

      relationshipsService.removeRelationshipCalls.awaitItem()
      onExitCalls.awaitItem()
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("Full Account TC goes through hardware auth before removal") {
    stateMachine.test(fullAccountProps) {
      // Initial sheet showing protected customer details
      awaitUntilSheet<FormBodyModel> {
        secondaryButton.shouldNotBeNull().onClick()
      }

      // Full-screen removal confirmation (no alert dialog for W3)
      awaitUntilBody<RemoveMyselfAsTrustedContactBodyModel> {
        protectedCustomerAlias.shouldBe(ProtectedCustomerFake.alias.alias)
        onRemove()
      }

      // Hardware auth screen
      awaitUntilBodyMock<HardwareAuthUiProps> {
        onSuccess(PrivilegedActionProof.HwKeyProof(HwFactorProofOfPossession("fake")))
      }

      relationshipsService.removeRelationshipCalls.awaitItem()
      onExitCalls.awaitItem()
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("Full Account TC can back out of hardware auth") {
    stateMachine.test(fullAccountProps) {
      // Initial sheet showing protected customer details
      awaitUntilSheet<FormBodyModel> {
        secondaryButton.shouldNotBeNull().onClick()
      }

      // Full-screen removal confirmation
      awaitUntilBody<RemoveMyselfAsTrustedContactBodyModel> {
        onRemove()
      }

      // Hardware auth screen - user backs out
      awaitUntilBodyMock<HardwareAuthUiProps> {
        onBack()
      }

      // Returns to removal confirmation
      awaitUntilBody<RemoveMyselfAsTrustedContactBodyModel> {
        onClosed()
      }

      // Returns to viewing protected customer sheet
      awaitUntilSheet<FormBodyModel> {
        secondaryButton.shouldNotBeNull().isLoading.shouldBeFalse()
      }
    }
  }

  test("Full Account TC action proof includes customer alias") {
    stateMachine.test(fullAccountProps) {
      // Initial sheet showing protected customer details
      awaitUntilSheet<FormBodyModel> {
        secondaryButton.shouldNotBeNull().onClick()
      }

      // Full-screen removal confirmation
      awaitUntilBody<RemoveMyselfAsTrustedContactBodyModel> {
        onRemove()
      }

      // Hardware auth screen - verify the action proof type includes the customer alias
      awaitUntilBodyMock<HardwareAuthUiProps> {
        actionProofType.shouldBeTypeOf<ActionProofType.RemoveRecoveryCustomer>().run {
          entityId.shouldBe(ProtectedCustomerFake.relationshipId)
          name.shouldBe(ProtectedCustomerFake.alias.alias)
        }
        onBack()
      }

      awaitUntilBody<RemoveMyselfAsTrustedContactBodyModel> {}
    }
  }
})
