package build.wallet.statemachine.core

import build.wallet.ui.model.Model
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class StateMachineModelTests : FunSpec({
  test("model returns correct key") {
    val dummy: DummyModel = DummyModel(someProperty = "some property value")
    dummy.key.shouldBe("build.wallet.statemachine.core.DummyModel")
  }
})

data class DummyModel(
  val someProperty: String,
) : Model()
