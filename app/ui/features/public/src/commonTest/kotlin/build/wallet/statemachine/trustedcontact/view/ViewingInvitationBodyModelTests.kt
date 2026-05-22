package build.wallet.statemachine.trustedcontact.view

import build.wallet.bitkey.relationships.BeneficiaryInvitationFake
import build.wallet.bitkey.relationships.InvitationFake
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.ui.model.icon.IconImage.LocalImage
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class ViewingInvitationBodyModelTests : FunSpec({
  test("recovery contact invitations use a leading design system v2 header") {
    val model = ViewingInvitationBodyModel(
      invitation = InvitationFake,
      isExpired = false,
      onRemove = {},
      onShare = {},
      onReinvite = {},
      onBack = {}
    )

    model.designSystemV2Model
      .shouldNotBeNull()
      .header
      .shouldNotBeNull()
      .apply {
        alignment.shouldBe(FormHeaderModel.Alignment.LEADING)
        iconModel
          .shouldNotBeNull()
          .iconImage
          .shouldBe(LocalImage(Icon.DotRecoveryContact))
      }
  }

  test("beneficiary invitations keep the legacy header") {
    val model = ViewingInvitationBodyModel(
      invitation = BeneficiaryInvitationFake,
      isExpired = false,
      onRemove = {},
      onShare = {},
      onReinvite = {},
      onBack = {}
    )

    model.designSystemV2Model.shouldBeNull()
  }
})
