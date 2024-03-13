package build.wallet.bitkey.socrec

import build.wallet.crypto.PublicKey
import build.wallet.encrypt.XCiphertext
import build.wallet.serialization.ByteStringAsHexSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okio.ByteString

@Serializable
data class SocialChallengeResponse(
  @SerialName("recovery_relationship_id")
  val recoveryRelationshipId: String,
  @SerialName("trusted_contact_recovery_pake_pubkey")
  val trustedContactRecoveryPakePubkey: PublicKey,
  @SerialName("recovery_pake_confirmation")
  @Serializable(with = ByteStringAsHexSerializer::class)
  val recoveryPakeConfirmation: ByteString,
  @SerialName("resealed_dek")
  val resealedDek: XCiphertext,
)
