package build.wallet.f8e.socrec.models

import build.wallet.bitkey.socrec.SocialChallenge
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SocialChallengeResponseBody(
  @SerialName("social_challenge")
  val challenge: SocialChallenge,
)
