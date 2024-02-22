package build.wallet.bitkey.socrec

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SocialChallenge(
  @SerialName("social_challenge_id")
  val challengeId: String,
  @SerialName("code")
  val code: String,
  @SerialName("responses")
  val responses: List<SocialChallengeResponse>,
)
