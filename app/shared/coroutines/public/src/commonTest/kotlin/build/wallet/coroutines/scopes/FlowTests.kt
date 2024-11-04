package build.wallet.coroutines.scopes

import app.cash.turbine.test
import io.kotest.core.coroutines.backgroundScope
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow

class FlowTests : FunSpec({
  coroutineTestScope = true

  test("mapState should appropriately map the state") {
    val flow = MutableStateFlow(1)

    flow.mapAsStateFlow(backgroundScope) {
      it + 3
    }.test {
      awaitItem().shouldBe(4)
    }
  }
})
