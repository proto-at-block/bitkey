package build.wallet.bitkey.socrec

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StartSocialChallengeRequest(
  @SerialName("trusted_contacts")
  val trustedContacts: List<StartSocialChallengeRequestTrustedContact>,
)
