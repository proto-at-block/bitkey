package build.wallet.f8e.error

import build.wallet.f8e.error.code.F8eClientErrorCode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents a specific client (4xx) error for a given network request to F8e
 *
 * @property category: The category of the error
 * @property code: The specific code for the error
 */
@Serializable
data class F8eClientError<T : F8eClientErrorCode>(
  @SerialName("category")
  val category: F8eClientErrorCategory,
  @SerialName("code")
  val code: T,
)
