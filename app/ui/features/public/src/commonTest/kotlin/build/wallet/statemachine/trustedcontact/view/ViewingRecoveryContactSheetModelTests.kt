package build.wallet.statemachine.trustedcontact.view

import build.wallet.bitkey.relationships.EndorsedBeneficiaryFake
import build.wallet.bitkey.relationships.EndorsedTrustedContactFake1
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.ui.model.icon.IconImage.LocalImage
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class ViewingRecoveryContactSheetModelTests : FunSpec({
  test("endorsed recovery contacts use the design system v2 header") {
    val model = ViewingTrustedContactSheetModel(
      contact = EndorsedTrustedContactFake1,
      onRemove = {},
      onClosed = {}
    )

    model.body
      .shouldBeInstanceOf<FormBodyModel>()
      .designSystemV2Model
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

  test("endorsed beneficiaries keep the legacy header") {
    val model = ViewingTrustedContactSheetModel(
      contact = EndorsedBeneficiaryFake,
      onRemove = {},
      onClosed = {}
    )

    model.body
      .shouldBeInstanceOf<FormBodyModel>()
      .designSystemV2Model
      .shouldBeNull()
  }
})
