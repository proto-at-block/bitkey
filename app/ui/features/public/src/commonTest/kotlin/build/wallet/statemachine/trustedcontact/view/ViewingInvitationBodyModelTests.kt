package build.wallet.statemachine.trustedcontact.view

import build.wallet.bitkey.relationships.BeneficiaryInvitationFake
import build.wallet.bitkey.relationships.InvitationFake
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.ui.model.icon.IconImage.LocalImage
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class ViewingInvitationBodyModelTests : FunSpec({
  test("recovery contact invitations use a leading recovery contact header") {
    val model = ViewingInvitationBodyModel(
      invitation = InvitationFake,
      isExpired = false,
      onRemove = {},
      onShare = {},
      onReinvite = {},
      onBack = {}
    )

    model.header
      .shouldNotBeNull()
      .apply {
        alignment.shouldBe(FormHeaderModel.Alignment.LEADING)
        iconModel
          .shouldNotBeNull()
          .iconImage
          .shouldBe(LocalImage(Icon.DotRecoveryContact))
      }
  }

  test("missing invite code only offers remove (no reinvite, no share)") {
    var removed = false
    val model = ViewingInvitationBodyModel(
      invitation = InvitationFake,
      isExpired = false,
      isCodeMissing = true,
      onRemove = { removed = true },
      onShare = {},
      onReinvite = {},
      onBack = {}
    )

    model.primaryButton.shouldBeNull()

    model.secondaryButton.shouldNotBeNull().apply {
      text.shouldContain("Remove")
      onClick.invoke()
    }
    removed.shouldBeTrue()

    model.header.shouldNotBeNull().sublineModel.shouldNotBeNull()
  }

  test("loading invite code disables share button") {
    val model = ViewingInvitationBodyModel(
      invitation = InvitationFake,
      isExpired = false,
      isCodeLoading = true,
      onRemove = {},
      onShare = {},
      onReinvite = {},
      onBack = {}
    )

    model.primaryButton.shouldNotBeNull().apply {
      text.shouldBe("Share Invite")
      isLoading.shouldBeTrue()
      isEnabled.shouldBeFalse()
    }
  }

  test("beneficiary invitations use the beneficiary header") {
    val model = ViewingInvitationBodyModel(
      invitation = BeneficiaryInvitationFake,
      isExpired = false,
      onRemove = {},
      onShare = {},
      onReinvite = {},
      onBack = {}
    )

    model.header.shouldNotBeNull().apply {
      alignment.shouldBe(FormHeaderModel.Alignment.CENTER)
      iconModel
        .shouldNotBeNull()
        .iconImage
        .shouldBe(LocalImage(Icon.ShieldPerson))
    }
  }
})
