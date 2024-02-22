package build.wallet.bitkey.socrec

import build.wallet.encrypt.XCiphertext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SocialChallengeResponse(
  @SerialName("recovery_relationship_id")
  val recoveryRelationshipId: String,
  @SerialName("shared_secret_ciphertext")
  val sharedSecretCiphertext: XCiphertext,
)
