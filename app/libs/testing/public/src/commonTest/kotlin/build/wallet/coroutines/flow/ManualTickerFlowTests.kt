package build.wallet.coroutines.flow

import app.cash.turbine.test
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.seconds

class ManualTickerFlowTests : FunSpec({
  test("tick emits to collectors") {
    val manual = ManualTickerFlow()

    manual.test {
      manual.tick()
      awaitItem().shouldBe(Unit)
    }
  }

  test("create returns a flow that emits ticks") {
    val manual = ManualTickerFlow()
    val flow = manual.create(1.seconds)

    flow.test {
      manual.tick()
      awaitItem().shouldBe(Unit)
    }
  }
})
