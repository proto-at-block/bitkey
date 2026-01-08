package build.wallet.firmware

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class TelemetryIdentifiersTest : FunSpec({
  test("identifiers for memfault") {
    val identifiers = TelemetryIdentifiers("326FP51915000104", "1.0.32", "app-a-dev", "w1a-dvt", "CORE:1.0.0/UXC:0.2.0")
    identifiers.hwRevisionWithSwType().shouldBe("dvt-app-a-dev")
  }
  test("identifiers for memfault include MCU suffix when present") {
    val identifiers =
      TelemetryIdentifiers(
        serial = "326FP51915000104",
        version = "1.0.32",
        swType = "app-a-dev",
        hwRevision = "w1a-dvt",
        mcuInfo = "CORE:1.0.0/UXC:0.2.0"
      )
    identifiers.hwRevisionWithSwType().shouldBe("dvt-app-a-dev")
  }
})
