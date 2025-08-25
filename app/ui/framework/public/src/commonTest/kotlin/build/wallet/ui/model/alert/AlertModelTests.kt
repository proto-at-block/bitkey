package build.wallet.ui.model.alert

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

class AlertModelTests : FunSpec({

  test("alert model is redacted") {
    val sensitiveTitle = "Secret user data 12345"
    val subline = "Sensitive information here"
    val model = ButtonAlertModel(
      title = sensitiveTitle,
      subline = subline,
      onDismiss = {},
      primaryButtonText = "Confirm",
      onPrimaryButtonClick = {}
    )

    model.title.shouldContain(sensitiveTitle)
    model.subline.shouldContain(subline)

    val modelString = model.toString()
    modelString.shouldNotContain(sensitiveTitle)
    modelString.shouldNotContain(subline)
  }
})
