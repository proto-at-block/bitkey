package build.wallet.f8e.relationships.models

import build.wallet.bitkey.relationships.ProtectedCustomer
import build.wallet.bitkey.relationships.UnendorsedTrustedContact
import build.wallet.f8e.relationships.F8eEndorsedTrustedContact
import build.wallet.ktor.result.RedactedResponseBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class GetRecoveryRelationshipsResponseBody(
  val invitations: List<RelationshipInvitation>,
  @SerialName("unendorsed_trusted_contacts")
  val unendorsedTrustedContacts: List<UnendorsedTrustedContact>,
  @SerialName("endorsed_trusted_contacts")
  val endorsedEndorsedTrustedContacts: List<F8eEndorsedTrustedContact>,
  val customers: List<ProtectedCustomer>,
) : RedactedResponseBody
