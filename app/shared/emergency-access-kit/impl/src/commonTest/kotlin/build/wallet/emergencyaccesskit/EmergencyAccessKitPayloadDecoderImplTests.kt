package build.wallet.emergencyaccesskit

import build.wallet.bitkey.spending.SpendingKeysetMock
import build.wallet.emergencyaccesskit.EmergencyAccessKitBackup.EmergencyAccessKitBackupV1
import build.wallet.emergencyaccesskit.EmergencyAccessKitPayload.EmergencyAccessKitPayloadV1
import build.wallet.emergencyaccesskit.EmergencyAccessKitPayloadDecoder.DecodeError
import build.wallet.emergencyaccesskit.EmergencyAccessKitPayloadDecoder.DecodeError.InvalidProtoData
import build.wallet.emergencyaccesskit.v1.ActiveSpendingKeysetV1
import build.wallet.emergencyaccesskit.v1.BackupV1
import build.wallet.emergencyaccesskit.v1.Payload
import build.wallet.testing.shouldBeErr
import build.wallet.testing.shouldBeErrOfType
import build.wallet.testing.shouldBeOk
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.ktor.utils.io.core.toByteArray
import okio.ByteString.Companion.toByteString

class EmergencyAccessKitPayloadDecoderImplTests : FunSpec({
  val decoder = EmergencyAccessKitPayloadDecoderImpl

  test("Full encode decode loop") {
    val ciphertext = "ciphertext".toByteArray().toByteString()
    val spendingKey = "spending keys".toByteArray().toByteString()

    val sourcePayload =
      EmergencyAccessKitPayloadV1(
        hwEncryptionKeyCiphertext = ciphertext,
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
      .toEmergencyAccessKitPayload()
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
      EmergencyAccessKitBackupV1(
        spendingKeyset = SpendingKeysetMock,
        appSpendingKeyXprv = "KeyXprv"
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
          )
      )

    decoder.decodeDecryptedBackup(encoded)
      .shouldBeOk(expected)
  }

  test("Backup missing fields results in decode error") {
    val source =
      EmergencyAccessKitBackupV1(
        spendingKeyset = SpendingKeysetMock,
        appSpendingKeyXprv = "KeyXprv"
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
})
