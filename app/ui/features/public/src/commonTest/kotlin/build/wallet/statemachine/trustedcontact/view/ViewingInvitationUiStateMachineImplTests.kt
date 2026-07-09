package build.wallet.statemachine.trustedcontact.view

import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.relationships.Invitation
import build.wallet.bitkey.relationships.InvitationFake
import build.wallet.bitkey.relationships.OutgoingInvitation
import build.wallet.coroutines.turbine.turbines
import build.wallet.platform.sharing.SharingManagerFake
import build.wallet.recovery.socrec.InviteCodeLoadError
import build.wallet.recovery.socrec.InviteCodeLoader
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.core.LabelModel.StringModel
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.trustedcontact.reinvite.ReinviteTrustedContactUiProps
import build.wallet.statemachine.trustedcontact.reinvite.ReinviteTrustedContactUiStateMachine
import build.wallet.statemachine.trustedcontact.remove.RemoveTrustedContactUiProps
import build.wallet.statemachine.trustedcontact.remove.RemoveTrustedContactUiStateMachine
import build.wallet.statemachine.ui.awaitBodyMock
import build.wallet.statemachine.ui.awaitUntilSheet
import build.wallet.time.ClockFake
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class ViewingInvitationUiStateMachineImplTests : FunSpec({
  val sharingManager = SharingManagerFake()
  val onExitCalls = turbines.create<Unit>("onExit")
  val clock = ClockFake()

  // Default loader returns Ok(code = "test-invite-code"). Tests override as needed.
  var loaderResult: Result<OutgoingInvitation, InviteCodeLoadError> =
    Ok(OutgoingInvitation(InvitationFake, inviteCode = "test-invite-code"))
  val inviteCodeLoader = object : InviteCodeLoader {
    override suspend fun getInviteCode(invitation: Invitation) = loaderResult
  }

  val stateMachine = ViewingInvitationUiStateMachineImpl(
    removeTrustedContactsUiStateMachine = object : RemoveTrustedContactUiStateMachine,
      ScreenStateMachineMock<RemoveTrustedContactUiProps>("remove-tc") {},
    reinviteTrustedContactUiStateMachine = object : ReinviteTrustedContactUiStateMachine,
      ScreenStateMachineMock<ReinviteTrustedContactUiProps>("reinvite-tc") {},
    sharingManager = sharingManager,
    clock = clock,
    inviteCodeLoader = inviteCodeLoader
  )

  fun props(invitation: Invitation = InvitationFake) = ViewingInvitationProps(
    hostScreen = ScreenModel(body = LoadingBodyModel(id = null)),
    fullAccount = FullAccountMock,
    invitation = invitation,
    onExit = { onExitCalls.add(Unit) }
  )

  beforeTest {
    loaderResult = Ok(OutgoingInvitation(InvitationFake, inviteCode = "test-invite-code"))
    sharingManager.lastSharedText.value = null
  }

  test("loaded code: share button enabled and shares the invite code") {
    stateMachine.test(props()) {
      awaitUntilSheet<ViewingInvitationBodyModel>(
        matching = { !it.isCodeLoading && !it.isCodeMissing }
      ) {
        primaryButton.shouldNotBeNull().apply {
          text.shouldBe("Share Invite")
          isEnabled.shouldBeTrue()
          isLoading.shouldBeFalse()
          onClick.invoke()
        }
      }

      sharingManager.lastSharedText.value.shouldNotBeNull().text.shouldContain("test-invite-code")
      // Share completion invokes onExit synchronously via SharingManagerFake.
      onExitCalls.awaitItem()
    }
  }

  test("missing code: no primary button, secondary Remove triggers remove flow") {
    loaderResult = Err(InviteCodeLoadError.MissingPakeData("rel-id"))

    stateMachine.test(props()) {
      awaitUntilSheet<ViewingInvitationBodyModel>(matching = { it.isCodeMissing }) {
        primaryButton.shouldBeNull()
        secondaryButton.shouldNotBeNull().apply {
          text.shouldContain("Remove")
          onClick.invoke()
        }
      }

      // After clicking remove we transition into the RemoveTrustedContact mock screen.
      awaitBodyMock<RemoveTrustedContactUiProps>(id = "remove-tc") {
        trustedContact.shouldBe(InvitationFake)
      }
    }
  }

  test("non-missing loader failure stays in loading state (not Missing)") {
    loaderResult = Err(InviteCodeLoadError.StorageError(RuntimeException("boom")))

    stateMachine.test(props()) {
      // We never transition to a Missing UI for transient/storage errors — the share
      // button stays in its loading state instead of pushing the user to remove the invite.
      awaitUntilSheet<ViewingInvitationBodyModel>(matching = { it.isCodeLoading }) {
        isCodeMissing.shouldBeFalse()
        primaryButton.shouldNotBeNull().apply {
          text.shouldBe("Share Invite")
          isLoading.shouldBeTrue()
          isEnabled.shouldBeFalse()
        }
      }
    }
  }

  test("missing code: subline calls out the missing invite code status") {
    loaderResult = Err(InviteCodeLoadError.MissingPakeData("rel-id"))

    stateMachine.test(props()) {
      awaitUntilSheet<ViewingInvitationBodyModel>(matching = { it.isCodeMissing }) {
        val labelModel = header.shouldNotBeNull().sublineModel.shouldNotBeNull()
        (labelModel as StringModel).string.shouldContain("couldn't find an invite code")
      }
    }
  }
})
