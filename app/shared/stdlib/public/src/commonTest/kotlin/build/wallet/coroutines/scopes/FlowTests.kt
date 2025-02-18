package build.wallet.coroutines.scopes

import app.cash.turbine.test
import build.wallet.coroutines.createBackgroundScope
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow

class FlowTests : FunSpec({
  test("mapState should appropriately map the state") {
    val flow = MutableStateFlow(1)

    flow.mapAsStateFlow(createBackgroundScope()) {
      it + 3
    }.test {
      awaitItem().shouldBe(4)
    }
  }
})
