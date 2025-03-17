package build.wallet.firmware

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class TelemetryIdentifiersTest : FunSpec({
  test("identifiers for memfault") {
    val identifiers = TelemetryIdentifiers("326FP51915000104", "1.0.32", "app-a-dev", "w1a-dvt")
    identifiers.hwRevisionWithSwType().shouldBe("dvt-app-a-dev")
  }
})
