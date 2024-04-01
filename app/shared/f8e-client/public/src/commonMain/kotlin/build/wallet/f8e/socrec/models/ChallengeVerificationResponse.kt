package build.wallet.f8e.socrec.models

import build.wallet.bitkey.socrec.ProtectedCustomerRecoveryPakeKey
import build.wallet.crypto.PublicKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Challenge verification response
 *
 * @property socialChallengeId - the id of the current active social challenge
 * @property protectedCustomerRecoveryPakePubkey - pake public key of the customer that is recovering
 * @property sealedDek - encrypted material of the customer that is recovering
 */
@Serializable
data class ChallengeVerificationResponse(
  @SerialName("social_challenge_id")
  val socialChallengeId: String,
  @SerialName("protected_customer_recovery_pake_pubkey")
  val protectedCustomerRecoveryPakePubkey: PublicKey<ProtectedCustomerRecoveryPakeKey>,
  @SerialName("sealed_dek")
  val sealedDek: String,
)
