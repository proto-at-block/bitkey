package build.wallet.f8e.socrec.models

import build.wallet.bitkey.socrec.ProtectedCustomer
import build.wallet.bitkey.socrec.UnendorsedTrustedContact
import build.wallet.f8e.socrec.F8eEndorsedTrustedContact
import build.wallet.ktor.result.RedactedResponseBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class GetRecoveryRelationshipsResponseBody(
  val invitations: List<CreateTrustedContactInvitation>,
  @SerialName("unendorsed_trusted_contacts")
  val unendorsedTrustedContacts: List<UnendorsedTrustedContact>,
  @SerialName("endorsed_trusted_contacts")
  val endorsedEndorsedTrustedContacts: List<F8eEndorsedTrustedContact>,
  val customers: List<ProtectedCustomer>,
) : RedactedResponseBody
