package bitkey.f8e.fingerprintreset

import bitkey.f8e.privilegedactions.CancelPrivilegedActionRequest
import bitkey.f8e.privilegedactions.PrivilegedActionsF8eClient
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.EmptyResponseBody
import build.wallet.ktor.result.RedactedResponseBody
import com.github.michaelbull.result.Result
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

interface FingerprintResetF8eClient :
  PrivilegedActionsF8eClient<FingerprintResetRequest, FingerprintResetResponse> {
  /**
   * Cancel a fingerprint reset privileged action
   */
  suspend fun cancelFingerprintReset(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    request: CancelPrivilegedActionRequest,
  ): Result<EmptyResponseBody, Throwable>
}

/**
 * Request to initiate a fingerprint reset
 */
@Serializable
data class FingerprintResetRequest(
  val version: Int,
  val action: Int,
  @SerialName("device_id")
  val deviceId: String,
  val challenge: String,
  val signature: String,
  @SerialName("hw_auth_public_key")
  val hwAuthPublicKey: String,
)

/**
 * Response for fingerprint reset after the delay and notify step is complete
 */
@Serializable
data class FingerprintResetResponse(
  val version: Int,
  @SerialName("serialized_request")
  val serializedRequest: String,
  val signature: String,
) : RedactedResponseBody
