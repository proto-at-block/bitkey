package build.wallet.f8e.socrec.models

import build.wallet.ktor.result.RedactedResponseBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class VerifyChallengeResponseBody(
  @SerialName("social_challenge")
  val challenge: ChallengeVerificationResponse,
) : RedactedResponseBody
