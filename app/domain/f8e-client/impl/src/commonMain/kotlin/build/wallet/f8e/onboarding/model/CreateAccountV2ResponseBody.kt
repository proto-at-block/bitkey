package build.wallet.f8e.onboarding.model

import build.wallet.ktor.result.RedactedResponseBody
import kotlinx.serialization.*

@Serializable
data class CreateAccountV2ResponseBody(
  @SerialName("account_id")
  val accountId: String,
  @SerialName("keyset_id")
  val keysetId: String,
  @SerialName("server_pub")
  val serverPub: String,
  @SerialName("server_pub_integrity_sig")
  val serverPubIntegritySig: String,
) : RedactedResponseBody
