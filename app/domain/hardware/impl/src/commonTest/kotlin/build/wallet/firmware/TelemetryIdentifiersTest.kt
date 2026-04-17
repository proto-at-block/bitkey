package build.wallet.firmware

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class TelemetryIdentifiersTest : FunSpec({
  test("W1 identifiers for memfault") {
    val identifiers = TelemetryIdentifiers("326FP51915000104", "1.0.32", "app-a-dev", "w1a-dvt", "CORE:1.0.0/UXC:0.2.0")
    identifiers.memfaultHwRevision().shouldBe("dvt")
    identifiers.hwRevisionWithSwType().shouldBe("dvt-app-a-dev")
  }

  test("W1 identifiers for memfault include MCU suffix when present") {
    val identifiers =
      TelemetryIdentifiers(
        serial = "326FP51915000104",
        version = "1.0.32",
        swType = "app-a-dev",
        hwRevision = "w1a-dvt",
        mcuInfo = "CORE:1.0.0/UXC:0.2.0"
      )
    identifiers.memfaultHwRevision().shouldBe("dvt")
    identifiers.hwRevisionWithSwType().shouldBe("dvt-app-a-dev")
  }

  test("W3 core identifiers preserve full hwRevision for memfault") {
    val identifiers =
      TelemetryIdentifiers(
        serial = "W3A0000000000001",
        version = "1.0.0",
        swType = "app-a-dev",
        hwRevision = "w3a-core-evt",
        mcuInfo = "CORE:EFR32:1.0.0"
      )
    identifiers.memfaultHwRevision().shouldBe("w3a-core-evt")
    identifiers.hwRevisionWithSwType().shouldBe("w3a-core-evt-app-a-dev")
  }

  test("W3 UXC identifiers preserve full hwRevision for memfault") {
    val identifiers =
      TelemetryIdentifiers(
        serial = "W3A0000000000001",
        version = "0.2.0",
        swType = "app-a-dev",
        hwRevision = "w3a-uxc-evt",
        mcuInfo = "UXC:STM32U5:0.2.0"
      )
    identifiers.memfaultHwRevision().shouldBe("w3a-uxc-evt")
    identifiers.hwRevisionWithSwType().shouldBe("w3a-uxc-evt-app-a-dev")
  }
})
