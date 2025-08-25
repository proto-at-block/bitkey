package build.wallet.ui.model.toast

import build.wallet.statemachine.core.Icon
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class ToastModelTests : FunSpec({

  test("toast model is redacted") {
    val sensitiveTitle = "Secret user data 12345"
    val model = ToastModel(
      leadingIcon = IconModel(
        icon = Icon.SmallIconInformationFilled,
        iconSize = IconSize.Small,
        iconTint = IconTint.Primary
      ),
      title = sensitiveTitle,
      id = "test-id-123",
      iconStrokeColor = ToastModel.IconStrokeColor.White
    )

    model.title.shouldContain(sensitiveTitle)

    val modelString = model.toString()
    modelString.shouldBe("ToastModel(██)")
  }
})
