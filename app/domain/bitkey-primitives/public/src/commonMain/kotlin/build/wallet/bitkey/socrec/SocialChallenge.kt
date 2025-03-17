package build.wallet.bitkey.socrec

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SocialChallenge(
  @SerialName("social_challenge_id")
  val challengeId: String,
  @SerialName("counter")
  val counter: Int,
  @SerialName("responses")
  val responses: List<SocialChallengeResponse>,
)
