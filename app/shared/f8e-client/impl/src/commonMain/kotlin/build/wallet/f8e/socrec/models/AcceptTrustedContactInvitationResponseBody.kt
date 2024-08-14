package build.wallet.f8e.socrec.models

import build.wallet.bitkey.relationships.ProtectedCustomer
import build.wallet.ktor.result.RedactedResponseBody
import kotlinx.serialization.Serializable

@Serializable
internal data class AcceptTrustedContactInvitationResponseBody(
  val customer: ProtectedCustomer,
) : RedactedResponseBody
