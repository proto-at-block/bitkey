package build.wallet.f8e.socrec.models

import build.wallet.bitkey.socrec.ProtectedCustomer
import build.wallet.bitkey.socrec.TrustedContact
import build.wallet.bitkey.socrec.UnendorsedTrustedContact
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class GetRecoveryRelationshipsResponseBody(
  val invitations: List<CreateTrustedContactInvitation>,
  @SerialName("unendorsed_trusted_contacts")
  val unendorsedTrustedContacts: List<UnendorsedTrustedContact>,
  @SerialName("endorsed_trusted_contacts")
  val endorsedTrustedContacts: List<TrustedContact>,
  val customers: List<ProtectedCustomer>,
)
