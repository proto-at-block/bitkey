package build.wallet.fwup

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

class FwupManifestParserTests : FunSpec({
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
    """{"manifest_version": "0.0.2",
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
    """{"manifest_version": "0.0.2",
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

  test("Update from A slot to B slot") {
    val result = fwup.parseFwupManifest(goodJson, "0.0.0", A, FwupMode.Normal)
    result.shouldBe(
      Ok(
        ParseFwupManifestSuccess(
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
        ParseFwupManifestSuccess(
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
        ParseFwupManifestSuccess(
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
})
