package bitkey.f8e.account

import build.wallet.encrypt.XCiphertext
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.encodeUtf8

/**
 * Pins the JSON wire shape shared with f8e's `WalletMetadataBackup` Rust model:
 * `wrapped_ssek` is Base64 (Kotlin's [build.wallet.cloud.backup.csek.SealedSsek]
 * `ByteString` serializer <-> Rust `#[serde_as(as = "Base64")] Vec<u8>`), and
 * `sealed_wallet_metadata_snapshot` is an opaque XChaCha20 ciphertext string.
 * Drift on either side silently breaks every note backup, so these shapes must
 * not change without a coordinated server migration.
 */
class WalletMetadataBackupF8eClientTests : FunSpec({

  val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
  }

  // "sealed-ssek" base64-encoded.
  val sealedSsekBase64 = "c2VhbGVkLXNzZWs="
  val snapshotCiphertext = "deadbeef.cafebabe.01"

  val backup = WalletMetadataBackup(
    sealedSsek = "sealed-ssek".encodeUtf8(),
    sealedWalletMetadataSnapshot = XCiphertext(snapshotCiphertext)
  )

  test("put request serializes wrapped_ssek as Base64 and snapshot as a string") {
    val result = json.encodeToString(WalletMetadataBackupRequestBody(backup))

    result.shouldEqualJson(
      """
      {
        "wrapped_ssek": "$sealedSsekBase64",
        "sealed_wallet_metadata_snapshot": "$snapshotCiphertext"
      }
      """
    )
  }

  test("get response deserializes a present backup") {
    val response = """
      {
        "wallet_metadata_backup": {
          "wrapped_ssek": "$sealedSsekBase64",
          "sealed_wallet_metadata_snapshot": "$snapshotCiphertext"
        }
      }
    """.trimIndent()

    val result = json.decodeFromString<GetWalletMetadataBackupResponseBody>(response)
    result.walletMetadataBackup.shouldBe(backup)
  }

  test("get response deserializes a missing backup as null") {
    val result = json.decodeFromString<GetWalletMetadataBackupResponseBody>(
      """{"wallet_metadata_backup": null}"""
    )
    result.walletMetadataBackup.shouldBeNull()
  }
})
