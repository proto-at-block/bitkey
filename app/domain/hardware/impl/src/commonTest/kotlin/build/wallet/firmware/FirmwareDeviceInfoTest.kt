package build.wallet.firmware

import build.wallet.firmware.FirmwareMetadata.FirmwareSlot
import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.test.logging.info
import io.kotest.matchers.shouldBe

class FirmwareDeviceInfoTest : FunSpec({
  val infoTemplate =
    FirmwareDeviceInfo(
      "0.0.00",
      "invalid-serial-number",
      "invalid-software-type",
      "invalid-hardware-revision",
      FirmwareSlot.A,
      83.23,
      4000,
      1,
      91,
      SecureBootConfig.NOT_SET,
      1234,
      null,
      mcuInfo = emptyList()
    )

  test("the firmware device fixture should be invalid") {
    infoTemplate.fwupHwVersion().shouldBe("revision")
  }

  test("dev config using secure boot config") {
    val info =
      infoTemplate.copy(
        version = "1.0.32",
        serial = "326FP51922222222",
        swType = "app-a-dev",
        secureBootConfig = SecureBootConfig.DEV,
        hwRevision = "w1-dvt"
      )
    info.fwupHwVersion().shouldBe("dvt")
  }

  test("prod config using secure boot config") {
    val info =
      infoTemplate.copy(
        version = "1.0.32",
        serial = "326FP51922222222",
        swType = "app-a-dev",
        secureBootConfig = SecureBootConfig.PROD,
        hwRevision = "w1-dvt"
      )
    info.fwupHwVersion().shouldBe("dvt-prod")
  }

  test("dev config") {
    val info =
      infoTemplate.copy(
        version = "1.0.32",
        serial = "326FP51915100104",
        swType = "app-a-dev",
        hwRevision = "w1-dvt"
      )
    info.fwupHwVersion().shouldBe("dvt")
  }

  test("prod config eng flag 8") {
    val info =
      infoTemplate.copy(
        version = "1.0.44",
        serial = "326FP51915800104",
        swType = "app-a-prod",
        hwRevision = "w1-dvt"
      )
    info.fwupHwVersion().shouldBe("dvt-prod")
  }

  test("prod config eng flag 9") {
    val info =
      infoTemplate.copy(
        version = "1.0.44",
        serial = "326FP51915900104",
        swType = "app-a-prod",
        hwRevision = "w1-dvt"
      )
    info.fwupHwVersion().shouldBe("dvt-prod")
  }

  test("prod config eng flag 0") {
    val info =
      infoTemplate.copy(
        version = "1.0.44",
        serial = "326FP51915000104",
        swType = "app-a-prod",
        hwRevision = "w1-dvt"
      )
    info.fwupHwVersion().shouldBe("dvt-prod")
  }

  test("prod device with dev firmware on it") {
    val info =
      infoTemplate.copy(
        "1.0.44",
        "326FP51915900104",
        "app-a-dev",
        "w1-dvt"
      )
    info.fwupHwVersion().shouldBe("dvt-prod")
  }

  test("dev device with prod firmware on it") {
    val info =
      infoTemplate.copy(
        "1.0.44",
        "326FP51915300104",
        "app-a-prod",
        "w1-dvt"
      )
    info.fwupHwVersion().shouldBe("dvt")
  }

  test("evt dev device with prod firmware on it") {
    val info =
      infoTemplate.copy(
        "1.0.44",
        "326FP51915300104",
        "app-a-prod",
        "w1-evt"
      )
    info.fwupHwVersion().shouldBe("evt-prod")
  }

  test("batteryChargeForUninitializedModelGauge returns correct SOC table") {
    // @formatter:off
    //
    // Hardcoded lookup table for SOC values
    // Generated the lookup table with:
    // def soc(c):
    //   return int(min(100, ((c * (100.0 / 85.0)))))
    // for v in list(range(0, 100)):
    //   print(f"{v} to {soc(v)},", end=" ")
    //
    val socLookupTable = mapOf(
      0 to 0, 1 to 1, 2 to 2, 3 to 3, 4 to 4, 5 to 5, 6 to 7, 7 to 8, 8 to 9, 9 to 10, 10 to 11,
      11 to 12, 12 to 14, 13 to 15, 14 to 16, 15 to 17, 16 to 18, 17 to 20, 18 to 21, 19 to 22,
      20 to 23, 21 to 24, 22 to 25, 23 to 27, 24 to 28, 25 to 29, 26 to 30, 27 to 31, 28 to 32,
      29 to 34, 30 to 35, 31 to 36, 32 to 37, 33 to 38, 34 to 40, 35 to 41, 36 to 42, 37 to 43,
      38 to 44, 39 to 45, 40 to 47, 41 to 48, 42 to 49, 43 to 50, 44 to 51, 45 to 52, 46 to 54,
      47 to 55, 48 to 56, 49 to 57, 50 to 58, 51 to 60, 52 to 61, 53 to 62, 54 to 63, 55 to 64,
      56 to 65, 57 to 67, 58 to 68, 59 to 69, 60 to 70, 61 to 71, 62 to 72, 63 to 74, 64 to 75,
      65 to 76, 66 to 77, 67 to 78, 68 to 80, 69 to 81, 70 to 82, 71 to 83, 72 to 84, 73 to 85,
      74 to 87, 75 to 88, 76 to 89, 77 to 90, 78 to 91, 79 to 92, 80 to 94, 81 to 95, 82 to 96,
      83 to 97, 84 to 98, 85 to 100, 86 to 100, 87 to 100, 88 to 100, 89 to 100, 90 to 100,
      91 to 100, 92 to 100, 93 to 100, 94 to 100, 95 to 100, 96 to 100, 97 to 100,
      98 to 100, 99 to 100
    )
    // @formatter:on

    socLookupTable.forEach { (chargeLevel, expectedSOC) ->
      val info = infoTemplate.copy(batteryCharge = chargeLevel.toDouble())
      val actualSOC = info.batteryChargeForUninitializedModelGauge()
      actualSOC.shouldBe(expectedSOC)
    }
  }
})
