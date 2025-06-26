package build.wallet.emergencyexitkit

import build.wallet.bitkey.app.AppSpendingPrivateKey
import build.wallet.bitkey.spending.AppSpendingPrivateKeyMock
import build.wallet.bitkey.spending.SpendingKeysetMock
import build.wallet.emergencyaccesskit.v1.ActiveSpendingKeysetV1
import build.wallet.emergencyaccesskit.v1.BackupV1
import build.wallet.emergencyaccesskit.v1.Payload
import build.wallet.emergencyexitkit.EmergencyExitKitBackup.EmergencyExitKitBackupV1
import build.wallet.emergencyexitkit.EmergencyExitKitPayload.EmergencyExitKitPayloadV1
import build.wallet.emergencyexitkit.EmergencyExitKitPayloadDecoder.DecodeError
import build.wallet.emergencyexitkit.EmergencyExitKitPayloadDecoder.DecodeError.InvalidProtoData
import build.wallet.testing.shouldBeErr
import build.wallet.testing.shouldBeErrOfType
import build.wallet.testing.shouldBeOk
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldNotContain
import io.ktor.utils.io.core.*
import okio.ByteString.Companion.EMPTY
import okio.ByteString.Companion.toByteString

class EmergencyExitKitPayloadDecoderImplTests : FunSpec({
  val decoder = EmergencyExitKitPayloadDecoderImpl()

  test("Full encode decode loop") {
    val ciphertext = "ciphertext".toByteArray().toByteString()
    val spendingKey = "spending keys".toByteArray().toByteString()

    val sourcePayload =
      EmergencyExitKitPayloadV1(
        sealedHwEncryptionKey = ciphertext,
        sealedActiveSpendingKeys =
          build.wallet.encrypt.SealedData(
            ciphertext = spendingKey,
            nonce = "nonce".toByteArray().toByteString(),
            tag = "tag".toByteArray().toByteString()
          )
      )

    val encodedString = decoder.encode(sourcePayload)
    decoder
      .decode(encodedString)
      .shouldBeOk(sourcePayload)
  }

  test("Missing fields return a decode error") {
    val source =
      Payload(
        backup_v1 =
          BackupV1(
            hw_encryption_key_ciphertext = null,
            sealed_active_spending_keyset = null
          )
      )
    val payload = Payload.ADAPTER.decode(source.encode())
    payload.backup_v1
      .shouldNotBeNull()
      .toEmergencyExitKitPayload()
      .shouldBeErr(InvalidProtoData())
  }

  test("Missing backup returns invalid backup version") {
    val source =
      Payload(backup_v1 = null)
    val payload = source.encode().encodeToBase58String()

    decoder.decode(payload)
      .shouldBeErr(DecodeError.InvalidBackupVersion)
  }

  test("Encode/decode loop for active spending keys fills in fake keyset ID") {
    val source =
      EmergencyExitKitBackupV1(
        spendingKeyset = SpendingKeysetMock,
        appSpendingKeyXprv = AppSpendingPrivateKeyMock
      )

    val encoded = decoder.encodeBackup(source)
    val expected =
      source.copy(
        spendingKeyset =
          source.spendingKeyset.copy(
            f8eSpendingKeyset =
              source.spendingKeyset.f8eSpendingKeyset.copy(
                keysetId = "FAKE_KEYSET_ID"
              )
          ),
        appSpendingKeyXprv = AppSpendingPrivateKey(
          key = source.appSpendingKeyXprv.key.copy(
            mnemonic = "MNEMONIC REMOVED DURING EMERGENCY ACCESS"
          )
        )
      )

    decoder.decodeDecryptedBackup(encoded)
      .shouldBeOk(expected)
  }

  test("Backup missing fields results in decode error") {
    val source =
      EmergencyExitKitBackupV1(
        spendingKeyset = SpendingKeysetMock,
        appSpendingKeyXprv = AppSpendingPrivateKeyMock
      )

    val intermediate =
      ActiveSpendingKeysetV1.ADAPTER.decode(decoder.encodeBackup(source))
        .copy(app_key = null)

    decoder.decodeDecryptedBackup(intermediate.encode().toByteString())
      .shouldBeErr(InvalidProtoData())
  }

  test("Invalid proto data returns an error") {
    decoder.decode(encodedString = "AABB")
      .shouldBeErrOfType<InvalidProtoData>()

    decoder.decodeDecryptedBackup(keysetData = "AABB".toByteArray().toByteString())
      .shouldBeErrOfType<InvalidProtoData>()
  }

  test("payload with whitespace or invalid base58 characters is still decoded") {
    val payload = EmergencyExitKitPayloadV1(
      sealedHwEncryptionKey = "sealedCsek".toByteArray().toByteString(),
      sealedActiveSpendingKeys =
        build.wallet.encrypt.SealedData(
          ciphertext = "sealedActiveSpendingKeys".toByteArray().toByteString(),
          nonce = "nonce".toByteArray().toByteString(),
          tag = EMPTY
        )
    )

    val encoded = decoder.encode(payload)

    var lines = arrayOf<String>()
    var currentLine = ""
    for ((index, ch) in encoded.withIndex()) {
      if (index > 0 && index % 10 == 0) {
        currentLine += ch
        lines += currentLine
        currentLine = ""
      } else {
        currentLine += ch
      }
    }
    if (currentLine.isNotEmpty()) {
      lines += currentLine
    }

    decoder.decode(lines.joinToString("\n"))
      .shouldBeOk(payload)
  }

  test("Private key is redacted in generated protos") {
    val backup = EmergencyExitKitBackupV1(
      spendingKeyset = SpendingKeysetMock,
      appSpendingKeyXprv = AppSpendingPrivateKeyMock
    )

    val encoded = decoder.encodeBackup(backup)
    val asProto = ActiveSpendingKeysetV1.ADAPTER.decode(encoded)

    asProto.app_key.toString()
      .shouldNotContain(AppSpendingPrivateKeyMock.key.xprv)
  }
})
