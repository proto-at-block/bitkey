package build.wallet.bitkey.socrec

import build.wallet.encrypt.Secp256k1PublicKey
import build.wallet.serialization.ByteStringAsHexSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okio.ByteString

@Serializable
data class KeyCertificate(
  @SerialName("trusted_contact_identity_pubkey")
  val trustedContactIdentityKey: TrustedContactIdentityKey,
  @SerialName("hw_endorsement_key")
  val hwEndorsementKey: Secp256k1PublicKey,
  @SerialName("app_endorsement_key")
  val appEndorsementKey: Secp256k1PublicKey,
  @SerialName("hw_signature")
  @Serializable(with = ByteStringAsHexSerializer::class)
  val hwSignature: ByteString,
  @SerialName("app_signature")
  @Serializable(with = ByteStringAsHexSerializer::class)
  val appSignature: ByteString,
)
