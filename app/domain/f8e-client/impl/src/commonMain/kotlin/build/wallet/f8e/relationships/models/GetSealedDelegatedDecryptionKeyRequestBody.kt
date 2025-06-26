package build.wallet.f8e.relationships.models

import bitkey.serialization.hex.ByteStringAsHexSerializer
import build.wallet.crypto.SealedData
import build.wallet.ktor.result.RedactedResponseBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class GetSealedDelegatedDecryptionKeyRequestBody(
  @SerialName("recovery_backup_material")
  @Serializable(with = ByteStringAsHexSerializer::class)
  val recoveryBackupMaterial: SealedData,
) : RedactedResponseBody
