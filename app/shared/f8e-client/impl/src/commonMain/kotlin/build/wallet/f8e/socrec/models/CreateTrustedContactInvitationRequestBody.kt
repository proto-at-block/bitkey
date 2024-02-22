package build.wallet.f8e.socrec.models

import build.wallet.bitkey.socrec.TrustedContactAlias
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class CreateTrustedContactInvitationRequestBody(
  @SerialName("trusted_contact_alias")
  val trustedContactAlias: TrustedContactAlias,
)
