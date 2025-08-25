package build.wallet.ui.model

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class ModelTests : FunSpec({

  test("model is redacted") {
    val sensitiveContent = "Secret user data 12345"
    val model = TestModel(data = sensitiveContent)

    model.shouldBeInstanceOf<TestModel>().data.shouldBe(sensitiveContent)

    val modelString = model.toString()
    modelString.shouldBe("TestModel(data=██)")
  }
})

private data class TestModel(
  val data: String,
) : Model
