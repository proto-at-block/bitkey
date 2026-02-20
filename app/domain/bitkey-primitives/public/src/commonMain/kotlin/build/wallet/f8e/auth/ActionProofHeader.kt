package build.wallet.f8e.auth

import kotlinx.serialization.Serializable

/**
 * Header containing the signed proof that a privileged action was authorized by the user's hardware key.
 * Submitted to f8e to authorize privileged operations.
 *
 * Serializes to JSON like:
 * ```json
 * {"version":1,"signatures":["<hex>"],"nonce":"<optional>"}
 * ```
 */
@Serializable
data class ActionProofHeader(
  val version: Int = 1,
  val signatures: List<String>,
  val nonce: String? = null,
)
