package bitkey.ui.sheets

import bitkey.ui.framework.Screen
import bitkey.ui.framework.test
import bitkey.ui.screens.trustedcontact.RemoveTrustedContactScreen
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.relationships.Invitation
import build.wallet.bitkey.relationships.InvitationFake
import build.wallet.bitkey.relationships.OutgoingInvitation
import build.wallet.platform.sharing.SharingManagerFake
import build.wallet.recovery.socrec.InviteCodeLoadError
import build.wallet.recovery.socrec.InviteCodeLoader
import build.wallet.statemachine.core.LabelModel.StringModel
import build.wallet.statemachine.trustedcontact.view.ViewingInvitationBodyModel
import build.wallet.statemachine.ui.awaitUntilSheet
import build.wallet.time.ClockFake
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf

class ViewInvitationSheetPresenterTests : FunSpec({
  val sharingManager = SharingManagerFake()
  val clock = ClockFake()
  var loaderResult: Result<OutgoingInvitation, InviteCodeLoadError> =
    Ok(OutgoingInvitation(InvitationFake, inviteCode = "test-invite-code"))
  val inviteCodeLoader = object : InviteCodeLoader {
    override suspend fun getInviteCode(invitation: Invitation) = loaderResult
  }

  val presenter = ViewInvitationSheetPresenter(
    sharingManager = sharingManager,
    clock = clock,
    inviteCodeLoader = inviteCodeLoader
  )
  val sheet = ViewInvitationSheet(
    account = FullAccountMock,
    invitation = InvitationFake,
    origin = object : Screen {}
  )

  beforeTest {
    loaderResult = Ok(OutgoingInvitation(InvitationFake, inviteCode = "test-invite-code"))
    sharingManager.lastSharedText.value = null
  }

  test("loaded code: share button shares the invite code and closes the sheet") {
    presenter.test(sheet) { navigator ->
      awaitUntilSheet<ViewingInvitationBodyModel>(
        matching = { !it.isCodeLoading && !it.isCodeMissing }
      ) {
        primaryButton.shouldNotBeNull().apply {
          text.shouldBe("Share Invite")
          onClick.invoke()
        }
      }

      sharingManager.lastSharedText.value.shouldNotBeNull().text
        .shouldContain("test-invite-code")
      navigator.closeSheetCalls.awaitItem()
    }
  }

  test("missing code: remove navigates to RemoveTrustedContactScreen") {
    loaderResult = Err(InviteCodeLoadError.MissingPakeData("rel-id"))

    presenter.test(sheet) { navigator ->
      awaitUntilSheet<ViewingInvitationBodyModel>(matching = { it.isCodeMissing }) {
        primaryButton.shouldBeNull()
        secondaryButton.shouldNotBeNull().apply {
          text.shouldContain("Remove")
          onClick.invoke()
        }
      }

      val target = navigator.goToCalls.awaitItem()
        .shouldBeInstanceOf<RemoveTrustedContactScreen>()
      target.trustedContact.shouldBe(InvitationFake)
      target.account.shouldBe(FullAccountMock)
    }
  }

  test("missing code: subline surfaces the missing-code copy") {
    loaderResult = Err(InviteCodeLoadError.MissingPakeData("rel-id"))

    presenter.test(sheet) { _ ->
      awaitUntilSheet<ViewingInvitationBodyModel>(matching = { it.isCodeMissing }) {
        val label = header.shouldNotBeNull().sublineModel.shouldNotBeNull()
        (label as StringModel).string.shouldContain("couldn't find an invite code")
      }
    }
  }
})
