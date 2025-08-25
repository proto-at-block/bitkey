package build.wallet.statemachine.core

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class SheetModelTests : FunSpec({

  test("sheet model is redacted") {
    val sensitiveBodyContent = "Secret user data 12345"
    val body = TestBodyModel(title = sensitiveBodyContent)

    val model = SheetModel(
      onClosed = {},
      body = body
    )

    model.body.shouldBeInstanceOf<TestBodyModel>().title.shouldBe(sensitiveBodyContent)

    val modelString = model.toString()
    modelString.shouldBe("SheetModel(██)")
  }
})

private data class TestBodyModel(
  val title: String,
) : BodyModel() {
  override val eventTrackerScreenInfo = null

  @Composable
  override fun render(modifier: Modifier) {
    // no-op
  }
}
