package build.wallet.f8e.socrec.models

import build.wallet.bitkey.socrec.StartSocialChallengeRequestTrustedContact
import build.wallet.ktor.result.RedactedRequestBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StartSocialChallengeRequestBody(
  @SerialName("trusted_contacts")
  val trustedContacts: List<StartSocialChallengeRequestTrustedContact>,
) : RedactedRequestBody
