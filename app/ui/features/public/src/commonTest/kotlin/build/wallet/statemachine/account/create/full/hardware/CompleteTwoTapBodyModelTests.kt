package build.wallet.statemachine.account.create.full.hardware

import build.wallet.analytics.events.screen.context.PairHardwareEventTrackerScreenIdContext
import build.wallet.statemachine.core.form.FormDesignSystemV2Model
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.send.hardwareconfirmation.HardwareConfirmationScreenModel
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class CompleteTwoTapBodyModelTests : FunSpec({

  test("design system v2 keeps onboarding copy and uses scan animation") {
    val completeTwoTapModel = CompleteTwoTapBodyModel(
      onBack = {},
      onContinue = {},
      onHelpClick = {},
      eventTrackerContext = PairHardwareEventTrackerScreenIdContext.ACCOUNT_CREATION
    )
    val hardwareConfirmationModel = HardwareConfirmationScreenModel(
      onBack = {},
      onConfirm = {}
    )

    val completeTwoTapDesignSystemV2Model = completeTwoTapModel.designSystemV2Model.shouldNotBeNull()
    val hardwareConfirmationDesignSystemV2Model = hardwareConfirmationModel.designSystemV2Model.shouldNotBeNull()

    completeTwoTapDesignSystemV2Model.scrollable.shouldBe(false)
    completeTwoTapDesignSystemV2Model.mainContentVerticalAlignment.shouldBe(
      FormDesignSystemV2Model.MainContentVerticalAlignment.CENTER
    )
    completeTwoTapDesignSystemV2Model.mainContentVerticalAlignment.shouldBe(
      hardwareConfirmationDesignSystemV2Model.mainContentVerticalAlignment
    )

    val completeTwoTapHeader = completeTwoTapDesignSystemV2Model
      .mainContentList.shouldNotBeNull()
      .single()
      .shouldBeInstanceOf<FormMainContentModel.HeaderBlock>()
      .header
    val hardwareConfirmationHeader = hardwareConfirmationDesignSystemV2Model
      .mainContentList.shouldNotBeNull()
      .single()
      .shouldBeInstanceOf<FormMainContentModel.HeaderBlock>()
      .header

    completeTwoTapHeader.headline.shouldBe("Review on your Bitkey")
    completeTwoTapHeader.sublineModel.shouldNotBeNull().string.shouldBe(
      "Follow the instructions on the device, then continue."
    )
    completeTwoTapHeader.headlineLabelType.shouldBe(hardwareConfirmationHeader.headlineLabelType)
    completeTwoTapHeader.customContent.shouldBe(hardwareConfirmationHeader.customContent)
    completeTwoTapHeader.bottomContent.shouldBe(hardwareConfirmationHeader.bottomContent)
  }
})
