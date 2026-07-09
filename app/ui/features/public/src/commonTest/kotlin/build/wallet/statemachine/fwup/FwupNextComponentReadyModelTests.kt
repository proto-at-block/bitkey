package build.wallet.statemachine.fwup

import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.form.FormMainContentVerticalAlignment
import build.wallet.statemachine.core.form.FormScreenLayoutModel
import build.wallet.ui.tokens.LabelType
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class FwupNextComponentReadyModelTests : FunSpec({
  test("centers the ready copy") {
    val model = FwupNextComponentReadyModel(
      completedIndex = 1,
      totalMcus = 2,
      onBack = {},
      onContinue = {}
    )

    model.formScreenLayout.shouldBe(
      FormScreenLayoutModel.LargeTitle(
        scrollable = false,
        mainContentVerticalAlignment = FormMainContentVerticalAlignment.CENTER
      )
    )

    val headerBlock = model.mainContentList
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
