package build.wallet.f8e.socrec.models

import build.wallet.bitkey.socrec.ProtectedCustomerEphemeralKey
import build.wallet.bitkey.socrec.ProtectedCustomerIdentityKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StartSocialChallengeRequestBody(
  @SerialName("customer_ephemeral_pubkey")
  val customerEphemeralPublicKey: ProtectedCustomerEphemeralKey,
  @SerialName("customer_identity_pubkey")
  val customerIdentityPublicKey: ProtectedCustomerIdentityKey,
)
