package build.wallet.f8e.socrec.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Challenge verification response
 *
 * @property socialChallengeId - the id of the current active social challenge
 * @property customerEphemeralPublicKey - the ephemeral public key of the customer that is recovering
 * @property customerIdentityPublicKey - the identity public key of the customer that is recovering
 */
@Serializable
data class ChallengeVerificationResponse(
  @SerialName("social_challenge_id")
  val socialChallengeId: String,
  @SerialName("customer_ephemeral_pubkey")
  val customerEphemeralPublicKey: String,
  @SerialName("customer_identity_pubkey")
  val customerIdentityPublicKey: String,
)
