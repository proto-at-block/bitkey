package bitkey.privilegedactions

import build.wallet.f8e.auth.ActionProofHeader
import com.github.michaelbull.result.Result
import uniffi.actionproof.Action
import uniffi.actionproof.Field

/**
 * Service for computing action proofs that bind privileged actions to authentication tokens.
 * Used to cryptographically link hardware-signed payloads to the user's current session.
 */
interface ActionProofService {
  /**
   * Computes the token binding for the current active account's auth token.
   * Returns a SHA256 hash binding the action proof to the user's JWT.
   */
  suspend fun computeTokenBinding(): Result<String, ActionProofError>

  /**
   * Builds the binding string for an action proof.
   * Automatically adds the token binding (tb) and nonce (n) if provided, then sorts alphabetically.
   *
   * @param extra Additional bindings to include (e.g., entity IDs)
   * @param nonce Optional nonce for replay protection (added as "n" binding)
   * @return Formatted binding string like "eid=abc,n=xyz,tb=def"
   */
  suspend fun buildBindings(
    extra: Map<String, String> = emptyMap(),
    nonce: String? = null,
  ): Result<String, ActionProofError>

  /**
   * Builds a canonical action payload for hardware signing.
   * Automatically computes and includes the token binding (tb) and nonce (n) if provided.
   *
   * @param action The action type (ADD, REMOVE, SET, DISABLE, ACCEPT)
   * @param field The field being modified
   * @param value Optional new value for the field
   * @param current Optional current value (for operations that require it)
   * @param extra Additional context bindings (e.g., entity IDs)
   * @param nonce Optional nonce for replay protection (added as "n" binding)
   * @return Binary payload ready for hardware signing
   */
  suspend fun buildPayload(
    action: Action,
    field: Field,
    value: String? = null,
    current: String? = null,
    extra: Map<String, String> = emptyMap(),
    nonce: String? = null,
  ): Result<ByteArray, ActionProofError>

  /**
   * Creates an ActionProofHeader from hardware signatures.
   *
   * @param signatures List of 65-byte hex-encoded signatures (130 chars each)
   * @param nonce Optional nonce for replay protection
   * @return ActionProofHeader ready for server submission
   */
  fun createActionProofHeader(
    signatures: List<String>,
    nonce: String? = null,
  ): Result<ActionProofHeader, ActionProofError>
}
