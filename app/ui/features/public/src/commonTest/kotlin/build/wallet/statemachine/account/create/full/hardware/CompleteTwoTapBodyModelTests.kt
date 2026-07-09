package build.wallet.statemachine.account.create.full.hardware

import build.wallet.analytics.events.screen.context.PairHardwareEventTrackerScreenIdContext
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.form.FormMainContentVerticalAlignment
import build.wallet.statemachine.core.form.FormScreenLayoutModel
import build.wallet.statemachine.send.hardwareconfirmation.HardwareConfirmationScreenModel
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class CompleteTwoTapBodyModelTests : FunSpec({

  test("keeps onboarding copy and uses scan animation") {
    val completeTwoTapModel = CompleteTwoTapBodyModel(
      onBack = {},
      onContinue = {},
      onHelpClick = {},
      eventTrackerContext = PairHardwareEventTrackerScreenIdContext.ACCOUNT_CREATION
    )
    val hardwareConfirmationModel = HardwareConfirmationScreenModel(
      onBack = {},
      onConfirm = {},
      onHelpClick = {}
    )

    completeTwoTapModel.formScreenLayout.shouldBe(
      FormScreenLayoutModel.LargeTitle(
        scrollable = false,
        mainContentVerticalAlignment = FormMainContentVerticalAlignment.CENTER
      )
    )
    completeTwoTapModel.formScreenLayout.shouldBe(hardwareConfirmationModel.formScreenLayout)

    val completeTwoTapHeader = completeTwoTapModel
      .mainContentList
      .single()
      .shouldBeInstanceOf<FormMainContentModel.HeaderBlock>()
      .header
    val hardwareConfirmationHeader = hardwareConfirmationModel
      .mainContentList
      .single()
      .shouldBeInstanceOf<FormMainContentModel.HeaderBlock>()
      .header

    completeTwoTapHeader.headline.shouldBe("Review on your Bitkey")
    completeTwoTapHeader.sublineModel.shouldNotBeNull().string.shouldBe(
      "Follow the instructions on the device, then continue."
    )
    completeTwoTapModel.toolbar.shouldNotBeNull().trailingAccessory.shouldNotBeNull()
    completeTwoTapHeader.headlineLabelType.shouldBe(hardwareConfirmationHeader.headlineLabelType)
    completeTwoTapHeader.customContent.shouldBe(hardwareConfirmationHeader.customContent)
    completeTwoTapHeader.bottomContent.shouldBe(hardwareConfirmationHeader.bottomContent)
  }
})
