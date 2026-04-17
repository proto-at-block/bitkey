package build.wallet.statemachine.fwup

import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.core.form.FormDesignSystemV2Model
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.ui.tokens.LabelType
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class FwupNextComponentReadyModelTests : FunSpec({
  test("legacy layout keeps the device showcase image") {
    val model = FwupNextComponentReadyModel(
      completedIndex = 1,
      totalMcus = 2,
      onBack = {},
      onContinue = {}
    )

    model.mainContentList.single()
      .shouldBeInstanceOf<FormMainContentModel.Showcase>()
      .content
      .shouldBeInstanceOf<FormMainContentModel.Showcase.Content.IconContent>()
      .icon
      .shouldBe(Icon.BitkeyDevice3D)
  }

  test("design system v2 removes the image and centers the text content") {
    val model = FwupNextComponentReadyModel(
      completedIndex = 1,
      totalMcus = 2,
      onBack = {},
      onContinue = {}
    )

    val designSystemV2Model = model.designSystemV2Model.shouldNotBeNull()
    designSystemV2Model.useLegacyHeaderFallback.shouldBe(false)
    designSystemV2Model.useDesignSystemV2ScreenLayout.shouldBe(true)
    designSystemV2Model.scrollable.shouldBe(false)
    designSystemV2Model.mainContentVerticalAlignment.shouldBe(
      FormDesignSystemV2Model.MainContentVerticalAlignment.CENTER
    )

    val headerBlock = designSystemV2Model.mainContentList
      .shouldNotBeNull()
      .single()
      .shouldBeInstanceOf<FormMainContentModel.HeaderBlock>()

    headerBlock.header.headline.shouldBe("Update 1 of 2 complete")
    headerBlock.header.sublineModel
      .shouldBeInstanceOf<LabelModel.StringModel>()
      .string
      .shouldBe("Press the button below and hold your unlocked device to the back of your phone to continue the update.")
    headerBlock.header.alignment.shouldBe(FormHeaderModel.Alignment.CENTER)
    headerBlock.header.headlineLabelType.shouldBe(LabelType.Body2Mono)
  }
})
