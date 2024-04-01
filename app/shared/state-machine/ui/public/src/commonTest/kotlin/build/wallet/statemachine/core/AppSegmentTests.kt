package build.wallet.statemachine.core

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class AppSegmentTests : FunSpec({
  test("id tag") {
    val aSegment = object : AppSegment {
      override val id: String = "a"
    }
    aSegment.id.shouldBe("a")

    val bSegment = aSegment.childSegment("b")
    bSegment.id.shouldBe("a.b")

    val cSegment = bSegment.childSegment("c")
    cSegment.id.shouldBe("a.b.c")
  }
})
