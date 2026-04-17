package bitkey.privilegedactions

import uniffi.actionproof.Action
import uniffi.actionproof.ContextBinding
import uniffi.actionproof.ContextBindingPair

class ActionProofFfiProviderFake : ActionProofFfiProvider {
  var computeTokenBindingResult: String = "fake-token-binding-abc123"
  var buildPayloadResult: List<UByte> = listOf(0x01u, 0x02u, 0x03u)

  val computeTokenBindingCalls = mutableListOf<String>()
  val buildPayloadCalls = mutableListOf<BuildPayloadCall>()

  data class BuildPayloadCall(
    val action: Action,
    val value: String?,
    val bindings: List<ContextBindingPair>,
  )

  override fun computeTokenBinding(accessToken: String): String {
    computeTokenBindingCalls.add(accessToken)
    return computeTokenBindingResult
  }

  override fun contextBindingKey(binding: ContextBinding): String =
    when (binding) {
      ContextBinding.TOKEN_BINDING -> "tb"
      ContextBinding.ENTITY_ID -> "eid"
      ContextBinding.NONCE -> "n"
    }

  override fun buildPayload(
    action: Action,
    value: String?,
    bindings: List<ContextBindingPair>,
  ): List<UByte> {
    buildPayloadCalls.add(BuildPayloadCall(action, value, bindings))
    return buildPayloadResult
  }

  fun reset() {
    computeTokenBindingResult = "fake-token-binding-abc123"
    buildPayloadResult = listOf(0x01u, 0x02u, 0x03u)
    computeTokenBindingCalls.clear()
    buildPayloadCalls.clear()
  }
}
