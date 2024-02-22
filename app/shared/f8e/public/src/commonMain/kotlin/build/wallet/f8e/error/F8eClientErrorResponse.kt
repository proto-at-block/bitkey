package build.wallet.f8e.error

import build.wallet.f8e.error.code.F8eClientErrorCode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response body for 4xx responses from the server
 */
@Serializable
data class F8eClientErrorResponse<T : F8eClientErrorCode>(
  @SerialName("errors")
  val errors: List<F8eClientError<T>>,
)
