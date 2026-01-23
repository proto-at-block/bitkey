package build.wallet.fwup

import build.wallet.firmware.McuName
import build.wallet.firmware.McuRole
import build.wallet.fwup.FwupManifestParser.FwupSlot.A
import build.wallet.fwup.FwupManifestParser.FwupSlot.B
import build.wallet.fwup.FwupManifestParser.ParseFwupManifestSuccess
import build.wallet.fwup.ParseFwupManifestError.ParsingError
import build.wallet.fwup.ParseFwupManifestError.UnknownManifestVersion
import build.wallet.testing.shouldBeErrOfType
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class FwupManifestParserTests :
  FunSpec({
    val fwup = FwupManifestParserImpl()
    val goodJson =
      """{"manifest_version": "0.0.1",
        |"fwup_bundle": {"product": "w1a",
        |"version": "1.2.5", "assets":
        |{"bootloader": {"image":
        |{"name": "w1a-proto-0-loader-dev.signed.bin"},
        |"signature":
        |{"name": "w1a-proto-0-loader-dev.detached_signature"}},
        |"application_a":
        |{"image": {"name": "w1a-proto-0-app-a-dev.signed.bin"},
        |"signature": {"name": "w1a-proto-0-app-a-dev.detached_signature"}},
        |"application_b": {"image": {"name": "w1a-proto-0-app-b-dev.signed.bin"},
        |"signature": {"name": "w1a-proto-0-app-b-dev.detached_signature"}}},
        |"parameters": {"wca_chunk_size": 452, "signature_offset": 647104,
        |"app_properties_offset": 1024}}}
      """.trimMargin()

    val wrongManifestVersionJson =
      """{"manifest_version": "0.0.3",
        |"fwup_bundle": {"product": "w1a",
        |"version": "1.2.5", "assets":
        |{"bootloader": {"image":
        |{"name": "w1a-proto-0-loader-dev.signed.bin"},
        |"signature":
        |{"name": "w1a-proto-0-loader-dev.detached_signature"}},
        |"application_a":
        |{"image": {"name": "w1a-proto-0-app-a-dev.signed.bin"},
        |"signature": {"name": "w1a-proto-0-app-a-dev.detached_signature"}},
        |"application_b": {"image": {"name": "w1a-proto-0-app-b-dev.signed.bin"},
        |"signature": {"name": "w1a-proto-0-app-b-dev.detached_signature"}}},
        |"parameters": {"wca_chunk_size": 452, "signature_offset": 647104,
        |"app_properties_offset": 1024}}}
      """.trimMargin()

    val missingSignatureFilesJson =
      """{"manifest_version": "0.0.3",
        |"fwup_bundle": {"product": "w1a",
        |"version": "1.2.5", "assets":
        |{"bootloader": {"image":
        |{"name": "w1a-proto-0-loader-dev.signed.bin"},
        |"application_a":
        |{"image": {"name": "w1a-proto-0-app-a-dev.signed.bin"},
        |"application_b": {"image": {"name": "w1a-proto-0-app-b-dev.signed.bin"},
        |"parameters": {"wca_chunk_size": 452, "signature_offset": 647104,
        |"app_properties_offset": 1024}}}
      """.trimMargin()

    val goodDeltaJson =
      """{"manifest_version": "0.0.1", 
      |"fwup_bundle": {"product": "w1a", 
      |"from_version": "1.0.16", 
      |"to_version": "1.0.30", "assets": 
      |{"a2b_patch": {"image": 
      |{"name": "w1a-dvt-a-to-b.signed.patch"}, 
      |"signature": 
      |{"name": "w1a-dvt-app-b-dev.detached_signature"}}, 
      |"b2a_patch": 
      |{"image": {"name": "w1a-dvt-b-to-a.signed.patch"}, 
      |"signature": {"name": "w1a-dvt-app-a-dev.detached_signature"}}}, 
      |"parameters": {"wca_chunk_size": 452, "signature_offset": 647104, 
      |"app_properties_offset": 1024}}}
      """.trimMargin()

    val goodV2Json =
      """{"manifest_version": "0.0.2",
        |"fwup_bundle": {"product": "w1a",
        |"version": "2.0.0",
        |"mcus": {
        |  "core": {
        |    "mcu_name": "efr32",
        |    "assets": {
        |      "bootloader": {"image": {"name": "main-loader.signed.bin"}, "signature": {"name": "main-loader.detached_signature"}},
        |      "application_a": {"image": {"name": "main-app-a.signed.bin"}, "signature": {"name": "main-app-a.detached_signature"}},
        |      "application_b": {"image": {"name": "main-app-b.signed.bin"}, "signature": {"name": "main-app-b.detached_signature"}}
        |    },
        |    "parameters": {"wca_chunk_size": 512, "signature_offset": 700000, "app_properties_offset": 2048}
        |  },
        |  "uxc": {
        |    "mcu_name": "stm32u5",
        |    "assets": {
        |      "application_a": {"image": {"name": "se-app-a.signed.bin"}, "signature": {"name": "se-app-a.detached_signature"}},
        |      "application_b": {"image": {"name": "se-app-b.signed.bin"}, "signature": {"name": "se-app-b.detached_signature"}}
        |    },
        |    "parameters": {"wca_chunk_size": 256, "signature_offset": 350000, "app_properties_offset": 1024}
        |  }
        |}}}
      """.trimMargin()

    val goodV2DeltaJson =
      """{"manifest_version": "0.0.2",
        |"fwup_bundle": {"product": "w1a",
        |"from_version": "1.5.0",
        |"to_version": "2.0.0",
        |"mcus": {
        |  "core": {
        |    "mcu_name": "efr32",
        |    "assets": {
        |      "a2b_patch": {"image": {"name": "main-a-to-b.signed.patch"}, "signature": {"name": "main-app-b.detached_signature"}},
        |      "b2a_patch": {"image": {"name": "main-b-to-a.signed.patch"}, "signature": {"name": "main-app-a.detached_signature"}}
        |    },
        |    "parameters": {"wca_chunk_size": 512, "signature_offset": 700000, "app_properties_offset": 2048}
        |  },
        |  "uxc": {
        |    "mcu_name": "stm32u5",
        |    "assets": {
        |      "a2b_patch": {"image": {"name": "se-a-to-b.signed.patch"}, "signature": {"name": "se-app-b.detached_signature"}},
        |      "b2a_patch": {"image": {"name": "se-b-to-a.signed.patch"}, "signature": {"name": "se-app-a.detached_signature"}}
        |    },
        |    "parameters": {"wca_chunk_size": 256, "signature_offset": 350000, "app_properties_offset": 1024}
        |  }
        |}}}
      """.trimMargin()

    test("Update from A slot to B slot") {
      val result = fwup.parseFwupManifest(goodJson, "0.0.0", A, FwupMode.Normal)
      result.shouldBe(
        Ok(
          ParseFwupManifestSuccess.SingleMcu(
            firmwareVersion = "1.2.5",
            binaryFilename = "w1a-proto-0-app-b-dev.signed.bin",
            signatureFilename = "w1a-proto-0-app-b-dev.detached_signature",
            chunkSize = 452U,
            signatureOffset = 647104U,
            appPropertiesOffset = 1024U
          )
        )
      )
    }

    // [W-1438] Re-enable.
    // test("No new update available") {
    //   val result = fwup.parseFwupManifest(goodJson, "1.2.5", A)
    //   result.shouldBe(Err(NO_UPDATE_NEEDED))
    // }

    test("Unknown manifest version") {
      val result = fwup.parseFwupManifest(wrongManifestVersionJson, "1.2.5", A, FwupMode.Normal)
      result.shouldBe(Err(UnknownManifestVersion))
    }

    test("Missing signature files") {
      val result = fwup.parseFwupManifest(missingSignatureFilesJson, "1.2.5", A, FwupMode.Normal)
      result.shouldBeErrOfType<ParsingError>()
    }

    test("Delta A to B") {
      val result = fwup.parseFwupManifest(goodDeltaJson, "1.0.1", A, FwupMode.Delta)
      result.shouldBe(
        Ok(
          ParseFwupManifestSuccess.SingleMcu(
            firmwareVersion = "1.0.30",
            binaryFilename = "w1a-dvt-a-to-b.signed.patch",
            signatureFilename = "w1a-dvt-app-b-dev.detached_signature",
            chunkSize = 452U,
            signatureOffset = 647104U,
            appPropertiesOffset = 1024U
          )
        )
      )
    }

    test("Delta B to A") {
      val result = fwup.parseFwupManifest(goodDeltaJson, "1.0.1", B, FwupMode.Delta)
      result.shouldBe(
        Ok(
          ParseFwupManifestSuccess.SingleMcu(
            firmwareVersion = "1.0.30",
            binaryFilename = "w1a-dvt-b-to-a.signed.patch",
            signatureFilename = "w1a-dvt-app-a-dev.detached_signature",
            chunkSize = 452U,
            signatureOffset = 647104U,
            appPropertiesOffset = 1024U
          )
        )
      )
    }

    test("Parsing normal manifest when trying to delta fwup") {
      val result = fwup.parseFwupManifest(goodJson, "1.0.1", A, FwupMode.Delta)
      result.shouldBeErrOfType<ParsingError>()
    }

    test("Update from A slot to B slot with V2 manifest") {
      val result = fwup.parseFwupManifest(goodV2Json, "0.0.0", A, FwupMode.Normal)
      result.shouldBe(
        Ok(
          ParseFwupManifestSuccess.MultiMcu(
            firmwareVersion = "2.0.0",
            mcuUpdates = mapOf(
              McuRole.CORE to ParseFwupManifestSuccess.McuUpdate(
                mcuName = McuName.EFR32,
                binaryFilename = "main-app-b.signed.bin",
                signatureFilename = "main-app-b.detached_signature",
                chunkSize = 512U,
                signatureOffset = 700000U,
                appPropertiesOffset = 2048U
              ),
              McuRole.UXC to ParseFwupManifestSuccess.McuUpdate(
                mcuName = McuName.STM32U5,
                binaryFilename = "se-app-b.signed.bin",
                signatureFilename = "se-app-b.detached_signature",
                chunkSize = 256U,
                signatureOffset = 350000U,
                appPropertiesOffset = 1024U
              )
            )
          )
        )
      )
    }

    test("Update from B slot to A slot with V2 manifest") {
      val result = fwup.parseFwupManifest(goodV2Json, "0.0.0", B, FwupMode.Normal)
      result.shouldBe(
        Ok(
          ParseFwupManifestSuccess.MultiMcu(
            firmwareVersion = "2.0.0",
            mcuUpdates = mapOf(
              McuRole.CORE to ParseFwupManifestSuccess.McuUpdate(
                mcuName = McuName.EFR32,
                binaryFilename = "main-app-a.signed.bin",
                signatureFilename = "main-app-a.detached_signature",
                chunkSize = 512U,
                signatureOffset = 700000U,
                appPropertiesOffset = 2048U
              ),
              McuRole.UXC to ParseFwupManifestSuccess.McuUpdate(
                mcuName = McuName.STM32U5,
                binaryFilename = "se-app-a.signed.bin",
                signatureFilename = "se-app-a.detached_signature",
                chunkSize = 256U,
                signatureOffset = 350000U,
                appPropertiesOffset = 1024U
              )
            )
          )
        )
      )
    }

    test("Delta A to B with V2 manifest") {
      val result = fwup.parseFwupManifest(goodV2DeltaJson, "1.0.0", A, FwupMode.Delta)
      result.shouldBe(
        Ok(
          ParseFwupManifestSuccess.MultiMcu(
            firmwareVersion = "2.0.0",
            mcuUpdates = mapOf(
              McuRole.CORE to ParseFwupManifestSuccess.McuUpdate(
                mcuName = McuName.EFR32,
                binaryFilename = "main-a-to-b.signed.patch",
                signatureFilename = "main-app-b.detached_signature",
                chunkSize = 512U,
                signatureOffset = 700000U,
                appPropertiesOffset = 2048U
              ),
              McuRole.UXC to ParseFwupManifestSuccess.McuUpdate(
                mcuName = McuName.STM32U5,
                binaryFilename = "se-a-to-b.signed.patch",
                signatureFilename = "se-app-b.detached_signature",
                chunkSize = 256U,
                signatureOffset = 350000U,
                appPropertiesOffset = 1024U
              )
            )
          )
        )
      )
    }

    test("Delta B to A with V2 manifest") {
      val result = fwup.parseFwupManifest(goodV2DeltaJson, "1.0.0", B, FwupMode.Delta)
      result.shouldBe(
        Ok(
          ParseFwupManifestSuccess.MultiMcu(
            firmwareVersion = "2.0.0",
            mcuUpdates = mapOf(
              McuRole.CORE to ParseFwupManifestSuccess.McuUpdate(
                mcuName = McuName.EFR32,
                binaryFilename = "main-b-to-a.signed.patch",
                signatureFilename = "main-app-a.detached_signature",
                chunkSize = 512U,
                signatureOffset = 700000U,
                appPropertiesOffset = 2048U
              ),
              McuRole.UXC to ParseFwupManifestSuccess.McuUpdate(
                mcuName = McuName.STM32U5,
                binaryFilename = "se-b-to-a.signed.patch",
                signatureFilename = "se-app-a.detached_signature",
                chunkSize = 256U,
                signatureOffset = 350000U,
                appPropertiesOffset = 1024U
              )
            )
          )
        )
      )
    }
  })
