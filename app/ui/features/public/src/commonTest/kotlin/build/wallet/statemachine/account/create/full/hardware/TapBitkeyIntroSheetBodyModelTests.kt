package build.wallet.statemachine.account.create.full.hardware

import build.wallet.platform.device.DevicePlatform
import build.wallet.statemachine.core.LabelModel.LinkSubstringModel
import build.wallet.statemachine.core.form.FormHeaderModel.Alignment.LEADING
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class TapBitkeyIntroSheetBodyModelTests : FunSpec({
  test("uses Android tap subtitle") {
    var learnMoreClicked = false
    var hasNoScreenClicked = false
    val model = TapBitkeyIntroSheetBodyModel(
      onDismiss = {},
      onLearnMore = { learnMoreClicked = true },
      onHasNoScreen = { hasNoScreenClicked = true },
      devicePlatform = DevicePlatform.Android
    )

    val header = model.header.shouldBeInstanceOf<build.wallet.statemachine.core.form.FormHeaderModel>()

    header.headline.shouldBe("How to tap your Bitkey")
    header.alignment.shouldBe(LEADING)
    header.sublineModel.shouldBeInstanceOf<LinkSubstringModel>().apply {
      string.shouldBe(
        "Tap your Bitkey with the screen side of your device facing the back of your phone. Learn more"
      )
      underline.shouldBe(true)
      bold.shouldBe(true)
      color.shouldBe(build.wallet.statemachine.core.LabelModel.Color.INVERSE)
      linkedSubstrings.single().onClick()
    }
    learnMoreClicked.shouldBe(true)
    model.primaryButton?.text.shouldBe("Got it")
    model.secondaryButton?.text.shouldBe("My Bitkey doesn't have a screen")
    model.secondaryButton?.onClick?.invoke()
    hasNoScreenClicked.shouldBe(true)
  }

  test("uses iOS tap subtitle") {
    val model = TapBitkeyIntroSheetBodyModel(
      onDismiss = {},
      onLearnMore = {},
      onHasNoScreen = {},
      devicePlatform = DevicePlatform.IOS
    )

    val header = model.header.shouldBeInstanceOf<build.wallet.statemachine.core.form.FormHeaderModel>()

    header.sublineModel.shouldBeInstanceOf<LinkSubstringModel>().string.shouldBe(
      "Tap your Bitkey along the top edge of your phone with the screen side of the device facing the back of your phone. Learn more"
    )
  }

  test("omits no-screen action when fallback is unavailable") {
    val model = TapBitkeyIntroSheetBodyModel(
      onDismiss = {},
      onLearnMore = {},
      onHasNoScreen = null,
      devicePlatform = DevicePlatform.Android
    )

    model.secondaryButton.shouldBeNull()
  }
})
