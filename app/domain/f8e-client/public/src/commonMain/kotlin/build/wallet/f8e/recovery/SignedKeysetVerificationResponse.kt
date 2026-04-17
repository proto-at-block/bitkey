package build.wallet.f8e.recovery

import build.wallet.ktor.result.RedactedResponseBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response from F8E containing signed keyset verification data.
 * Used for W3 hardware descriptor validation during Delay & Notify recovery.
 */
@Serializable
data class SignedKeysetVerificationResponse(
  @SerialName("app_auth_pub")
  val appAuthPub: String,
  @SerialName("hardware_auth_pub")
  val hardwareAuthPub: String,
  @SerialName("app_spending_pub")
  val appSpendingPub: String,
  @SerialName("hardware_spending_pub")
  val hardwareSpendingPub: String,
  @SerialName("server_spending_pub")
  val serverSpendingPub: String,
  @SerialName("signature")
  val signature: String,
) : RedactedResponseBody
