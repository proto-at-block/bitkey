package build.wallet.bitkey.socrec

import build.wallet.crypto.PublicKey
import build.wallet.encrypt.XCiphertext
import build.wallet.serialization.ByteStringAsHexSerializer
import dev.zacsweers.redacted.annotations.Redacted
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okio.ByteString

@Serializable
data class SocialChallengeResponse(
  @SerialName("recovery_relationship_id")
  val recoveryRelationshipId: String,
  @SerialName("trusted_contact_recovery_pake_pubkey")
  val trustedContactRecoveryPakePubkey: PublicKey<TrustedContactRecoveryPakeKey>,
  @Redacted
  @SerialName("recovery_pake_confirmation")
  @Serializable(with = ByteStringAsHexSerializer::class)
  val recoveryPakeConfirmation: ByteString,
  @Redacted
  @SerialName("resealed_dek")
  val resealedDek: XCiphertext,
)
