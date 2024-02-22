package build.wallet.f8e.socrec.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class AcceptTrustedContactInvitationRequestBody(
  val action: String,
  val code: String,
  @SerialName("customer_alias")
  val customerAlias: String,
  @SerialName("trusted_contact_identity_pubkey")
  val trustedContactIdentityPubkey: String,
) {
  constructor(
    code: String,
    customerAlias: String,
    trustedContactIdentityKey: String,
  ) : this(
    action = "Accept",
    code = code,
    customerAlias = customerAlias,
    trustedContactIdentityPubkey = trustedContactIdentityKey
  )
}
