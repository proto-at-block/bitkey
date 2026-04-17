package bitkey.ui.screens.trustedcontact

import bitkey.ui.framework.test
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.relationships.InvitationFake
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
import build.wallet.statemachine.trustedcontact.reinvite.ReinviteContactBodyModel
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitBodyMock
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.time.ClockFake
import com.github.michaelbull.result.Err
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class ReinviteTrustedContactScreenPresenterTests : FunSpec({
  val clock = ClockFake()
  val relationshipsService = RelationshipsServiceMock(turbines::create, clock)

  val hardwareAuthUiStateMachine =
    object : HardwareAuthUiStateMachine,
      ScreenStateMachineMock<HardwareAuthUiProps>(id = "hw-auth") {}

  val presenter = ReinviteTrustedContactScreenPresenter(
    hardwareAuthUiStateMachine = hardwareAuthUiStateMachine,
    sharingManager = SharingManagerFake(),
    clipboard = ClipboardMock(),
    relationshipsService = relationshipsService
  )

  val screen = ReinviteTrustedContactScreen(
    account = FullAccountMock,
    invitation = InvitationFake,
    origin = object : bitkey.ui.framework.Screen {}
  )

  beforeTest {
    relationshipsService.clear()
  }

  test("refreshInvitation failure shows error screen with FailedToSaveState") {
    relationshipsService.refreshInvitationResult =
      Err(HttpError.NetworkError(Throwable("network error")))

    presenter.test(screen) { _ ->
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

  test("refreshInvitation failure back button returns to save request screen") {
    relationshipsService.refreshInvitationResult =
      Err(HttpError.NetworkError(Throwable("network error")))

    presenter.test(screen) { _ ->
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
        trustedContactName.shouldBe("trustedContactAlias fake")
      }
    }
  }
})
