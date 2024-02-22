package build.wallet.f8e.socrec.models

import build.wallet.bitkey.socrec.ProtectedCustomer
import build.wallet.bitkey.socrec.TrustedContact
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class GetRecoveryRelationshipsResponseBody(
  val invitations: List<CreateTrustedContactInvitation>,
  @SerialName("trusted_contacts")
  val trustedContacts: List<TrustedContact>,
  val customers: List<ProtectedCustomer>,
)
