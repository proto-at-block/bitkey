package build.wallet.bitkey.socrec

import build.wallet.crypto.PublicKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StartSocialChallengeRequestTrustedContact(
  @SerialName("recovery_relationship_id")
  val recoveryRelationshipId: String,
  @SerialName("protected_customer_recovery_pake_pubkey")
  val protectedCustomerRecoveryPakePubkey: PublicKey<ProtectedCustomerRecoveryPakeKey>,
  @SerialName("sealed_dek")
  val sealedDek: String,
)
