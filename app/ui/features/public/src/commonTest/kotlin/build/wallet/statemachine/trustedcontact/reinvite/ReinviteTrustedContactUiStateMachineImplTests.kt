package build.wallet.statemachine.trustedcontact.reinvite

import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.auth.PrivilegedActionProof
import build.wallet.ktor.result.HttpError
import build.wallet.platform.clipboard.ClipboardMock
import build.wallet.platform.sharing.SharingManagerFake
import build.wallet.relationships.RelationshipsServiceMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.auth.HardwareAuthUiProps
import build.wallet.statemachine.auth.HardwareAuthUiStateMachine
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitBodyMock
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.time.ClockFake
import com.github.michaelbull.result.Err
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class ReinviteTrustedContactUiStateMachineImplTests : FunSpec({
  val clock = ClockFake()
  val exitCalls = turbines.create<Unit>("Exit Calls")
  val successCalls = turbines.create<Unit>("Success Calls")

  val relationshipsService = RelationshipsServiceMock(turbines::create, clock)

  val hardwareAuthUiStateMachine =
    object : HardwareAuthUiStateMachine,
      ScreenStateMachineMock<HardwareAuthUiProps>(id = "hw-auth") {}

  val stateMachine = ReinviteTrustedContactUiStateMachineImpl(
    hardwareAuthUiStateMachine = hardwareAuthUiStateMachine,
    sharingManager = SharingManagerFake(),
    clipboard = ClipboardMock(),
    relationshipsService = relationshipsService
  )

  val props = ReinviteTrustedContactUiProps(
    account = FullAccountMock,
    trustedContactAlias = "Alice",
    relationshipId = "test-relationship-id",
    isBeneficiary = false,
    onExit = { exitCalls.add(Unit) },
    onSuccess = { successCalls.add(Unit) }
  )

  beforeTest {
    relationshipsService.clear()
  }

  test("refreshInvitation failure shows error screen for recovery contact") {
    relationshipsService.refreshInvitationResult =
      Err(HttpError.NetworkError(Throwable("network error")))

    stateMachine.test(props) {
      awaitBody<ReinviteContactBodyModel> {
        onSave()
      }
      awaitBodyMock<HardwareAuthUiProps> {
        onSuccess(
          PrivilegedActionProof.HwKeyProof(HwFactorProofOfPossession("test-token"))
        )
      }
      awaitUntilBody<FormBodyModel> {
        header.shouldNotBeNull().headline.shouldContain("Unable to save")
        header.shouldNotBeNull().headline.shouldContain("Recovery Contact")
        primaryButton.shouldNotBeNull().text.shouldBe("Retry")
      }
    }
  }

  test("refreshInvitation failure shows error screen for beneficiary") {
    relationshipsService.refreshInvitationResult =
      Err(HttpError.NetworkError(Throwable("network error")))

    val beneficiaryProps = props.copy(isBeneficiary = true)

    stateMachine.test(beneficiaryProps) {
      awaitBody<ReinviteContactBodyModel> {
        onSave()
      }
      awaitBodyMock<HardwareAuthUiProps> {
        onSuccess(
          PrivilegedActionProof.HwKeyProof(HwFactorProofOfPossession("test-token"))
        )
      }
      awaitUntilBody<FormBodyModel> {
        header.shouldNotBeNull().headline.shouldContain("Unable to save")
        header.shouldNotBeNull().headline.shouldContain("beneficiary")
        primaryButton.shouldNotBeNull().text.shouldBe("Retry")
      }
    }
  }

  test("refreshInvitation failure back button returns to save request screen") {
    relationshipsService.refreshInvitationResult =
      Err(HttpError.NetworkError(Throwable("network error")))

    stateMachine.test(props) {
      awaitBody<ReinviteContactBodyModel> {
        onSave()
      }
      awaitBodyMock<HardwareAuthUiProps> {
        onSuccess(
          PrivilegedActionProof.HwKeyProof(HwFactorProofOfPossession("test-token"))
        )
      }
      awaitUntilBody<FormBodyModel> {
        secondaryButton.shouldNotBeNull().onClick()
      }
      awaitBody<ReinviteContactBodyModel> {
        trustedContactName.shouldBe("Alice")
      }
    }
  }
})
