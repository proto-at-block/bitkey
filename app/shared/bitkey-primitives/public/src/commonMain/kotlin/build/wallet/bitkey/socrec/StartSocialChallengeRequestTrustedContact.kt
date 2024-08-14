package build.wallet.bitkey.socrec

import build.wallet.crypto.PublicKey
import dev.zacsweers.redacted.annotations.Redacted
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StartSocialChallengeRequestTrustedContact(
  @SerialName("recovery_relationship_id")
  val recoveryRelationshipId: String,
  @SerialName("protected_customer_recovery_pake_pubkey")
  val protectedCustomerRecoveryPakePubkey: PublicKey<ProtectedCustomerRecoveryPakeKey>,
  @Redacted
  @SerialName("sealed_dek")
  val sealedDek: String,
)
