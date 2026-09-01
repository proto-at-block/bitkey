package build.wallet.statemachine.trustedcontact.view

import build.wallet.bitkey.relationships.EndorsedBeneficiaryFake
import build.wallet.bitkey.relationships.EndorsedTrustedContactFake1
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.ui.model.icon.IconImage.LocalImage
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class ViewingRecoveryContactSheetModelTests : FunSpec({
  test("endorsed recovery contacts use the recovery contact header") {
    val model = ViewingTrustedContactSheetModel(
      contact = EndorsedTrustedContactFake1,
      onRemove = {},
      onClosed = {}
    )

    model.body
      .shouldBeInstanceOf<FormBodyModel>()
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

  test("tampered sheet supports endorsed recovery contacts") {
    val model = ViewingTamperedContactSheetModel(
      contact = EndorsedTrustedContactFake1,
      onRemove = {},
      onClosed = {}
    )

    model.body
      .shouldBeInstanceOf<FormBodyModel>()
      .header
      .shouldNotBeNull()
      .apply {
        headline.shouldBe(
          "someContact is no longer listed as a valid Recovery Contact"
        )
        alignment.shouldBe(FormHeaderModel.Alignment.LEADING)
        iconModel
          .shouldNotBeNull()
          .iconImage
          .shouldBe(LocalImage(Icon.DotRecoveryContact))
      }
  }

  test("tampered sheet supports endorsed beneficiaries") {
    val model = ViewingTamperedContactSheetModel(
      contact = EndorsedBeneficiaryFake,
      onRemove = {},
      onClosed = {}
    )

    model.body
      .shouldBeInstanceOf<FormBodyModel>()
      .apply {
        header
          .shouldNotBeNull()
          .apply {
            headline.shouldBe(
              "endorsedBeneficiaryAlias is no longer listed as a valid beneficiary"
            )
            alignment.shouldBe(FormHeaderModel.Alignment.CENTER)
          }
        secondaryButton
          .shouldNotBeNull()
          .text
          .shouldBe("Remove beneficiary")
      }
  }

  test("unverified sheet shows re-verification guidance for placeholder endorsements") {
    val model = ViewingUnverifiedContactSheetModel(
      contact = EndorsedTrustedContactFake1,
      onRemove = {},
      onClosed = {}
    )

    model.body
      .shouldBeInstanceOf<FormBodyModel>()
      .apply {
        header
          .shouldNotBeNull()
          .apply {
            headline.shouldBe("someContact needs to be re-verified")
            sublineModel
              .shouldNotBeNull()
              .shouldBeInstanceOf<LabelModel.StringModel>()
              .string
              .shouldBe(
                "Your Recovery Contact needs to be re-verified with your Bitkey device. " +
                  "Tap the “Your wallet is at risk” alert on the Home screen to verify " +
                  "with your device, and this will be resolved automatically."
              )
          }
        secondaryButton
          .shouldNotBeNull()
          .text
          .shouldBe("Remove contact")
      }
  }

  test("unverified sheet supports beneficiaries") {
    val model = ViewingUnverifiedContactSheetModel(
      contact = EndorsedBeneficiaryFake,
      onRemove = {},
      onClosed = {}
    )

    model.body
      .shouldBeInstanceOf<FormBodyModel>()
      .apply {
        header
          .shouldNotBeNull()
          .headline
          .shouldBe("endorsedBeneficiaryAlias needs to be re-verified")
        secondaryButton
          .shouldNotBeNull()
          .text
          .shouldBe("Remove beneficiary")
      }
  }

  test("endorsed beneficiaries use the beneficiary header") {
    val model = ViewingTrustedContactSheetModel(
      contact = EndorsedBeneficiaryFake,
      onRemove = {},
      onClosed = {}
    )

    model.body
      .shouldBeInstanceOf<FormBodyModel>()
      .header
      .shouldNotBeNull()
      .apply {
        alignment.shouldBe(FormHeaderModel.Alignment.CENTER)
        iconModel
          .shouldNotBeNull()
          .iconImage
          .shouldBe(LocalImage(Icon.ShieldPerson))
      }
  }
})
