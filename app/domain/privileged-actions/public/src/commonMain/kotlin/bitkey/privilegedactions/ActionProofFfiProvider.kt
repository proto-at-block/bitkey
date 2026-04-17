package bitkey.privilegedactions

import uniffi.actionproof.Action
import uniffi.actionproof.ContextBinding
import uniffi.actionproof.ContextBindingPair

/**
 * Abstraction over the actionproof UniFFI bindings to enable unit testing
 * without loading native libraries.
 */
interface ActionProofFfiProvider {
  /**
   * Computes a token binding hash from the access token.
   */
  fun computeTokenBinding(accessToken: String): String

  /**
   * Returns the canonical key name for a context binding type.
   */
  fun contextBindingKey(binding: ContextBinding): String

  /**
   * Builds a canonical payload for the given action, values, and bindings.
   */
  fun buildPayload(
    action: Action,
    value: String?,
    bindings: List<ContextBindingPair>,
  ): List<UByte>
}
