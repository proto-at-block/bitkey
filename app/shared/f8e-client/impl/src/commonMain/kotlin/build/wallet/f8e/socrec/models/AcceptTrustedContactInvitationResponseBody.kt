package build.wallet.f8e.socrec.models

import build.wallet.bitkey.socrec.ProtectedCustomer
import kotlinx.serialization.Serializable

@Serializable
internal data class AcceptTrustedContactInvitationResponseBody(
  val customer: ProtectedCustomer,
)
