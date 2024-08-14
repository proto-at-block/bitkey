package build.wallet.f8e.socrec.models

import build.wallet.bitkey.relationships.UnendorsedTrustedContact
import build.wallet.f8e.socrec.F8eEndorsedTrustedContact
import build.wallet.ktor.result.RedactedResponseBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class EndorseTrustedContactsResponseBody(
  @SerialName("unendorsed_trusted_contacts")
  val unendorsedTrustedContacts: List<UnendorsedTrustedContact>,
  @SerialName("endorsed_trusted_contacts")
  val endorsedEndorsedTrustedContacts: List<F8eEndorsedTrustedContact>,
) : RedactedResponseBody
