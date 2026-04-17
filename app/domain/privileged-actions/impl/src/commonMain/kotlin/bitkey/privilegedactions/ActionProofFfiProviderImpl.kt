package bitkey.privilegedactions

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import uniffi.actionproof.Action
import uniffi.actionproof.ContextBinding
import uniffi.actionproof.ContextBindingPair

@BitkeyInject(AppScope::class)
class ActionProofFfiProviderImpl : ActionProofFfiProvider {
  override fun computeTokenBinding(accessToken: String): String =
    uniffi.actionproof.computeTokenBinding(accessToken)

  override fun contextBindingKey(binding: ContextBinding): String =
    uniffi.actionproof.contextBindingKey(binding)

  override fun buildPayload(
    action: Action,
    value: String?,
    bindings: List<ContextBindingPair>,
  ): List<UByte> = uniffi.actionproof.buildPayload(action, value, bindings)
}
