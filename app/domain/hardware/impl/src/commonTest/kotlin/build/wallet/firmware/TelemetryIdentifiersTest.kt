package build.wallet.firmware

import build.wallet.firmware.FirmwareMetadata.FirmwareSlot
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
        mcuInfo = "CORE:EFR32:1.0.0",
        mcuRole = McuRole.CORE,
        activeSlot = FirmwareSlot.A
      )
    identifiers.memfaultHwRevision().shouldBe("w3a-core-evt")
    identifiers.memfaultSoftwareType().shouldBe("app-a-dev")
    identifiers.hwRevisionWithSwType().shouldBe("w3a-core-evt-app-a-dev")
  }

  test("W3 UXC identifiers preserve UXC hwRevision for memfault") {
    val identifiers =
      TelemetryIdentifiers(
        serial = "W3A0000000000001",
        version = "0.2.0",
        swType = "app-a-dev",
        hwRevision = "w3a-core-evt",
        mcuInfo = "UXC:STM32U5:0.2.0",
        mcuRole = McuRole.UXC,
        activeSlot = FirmwareSlot.A
      )
    identifiers.memfaultHwRevision().shouldBe("w3a-uxc-evt")
    identifiers.memfaultSoftwareType().shouldBe("app-a-dev")
    identifiers.hwRevisionWithSwType().shouldBe("w3a-uxc-evt-app-a-dev")
  }

  test("W3 UXC identifiers use per-MCU active slot for memfault software type") {
    val identifiers =
      TelemetryIdentifiers(
        serial = "W3A0000000000001",
        version = "1.2.3",
        swType = "app-a-dev",
        hwRevision = "w3a-core-pdvt",
        mcuInfo = "UXC:STM32U5:1.2.3",
        mcuRole = McuRole.UXC,
        activeSlot = FirmwareSlot.B
      )
    identifiers.memfaultHwRevision().shouldBe("w3a-uxc-pdvt")
    identifiers.memfaultSoftwareType().shouldBe("app-b-dev")
    identifiers.hwRevisionWithSwType().shouldBe("w3a-uxc-pdvt-app-b-dev")
  }
})
