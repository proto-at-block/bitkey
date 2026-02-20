package bitkey.privilegedactions

import build.wallet.f8e.auth.ActionProofHeader
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import uniffi.actionproof.Action
import uniffi.actionproof.Field

class ActionProofServiceFake : ActionProofService {
  private val defaultTokenBinding = "59dc8eb2a2c5a8e3d0b7f4c6e1a9d2b5c8f0e3a6d9c2b5a8f1e4d7c0b3a6f9e2"
  private val defaultPayload = ByteArray(32) { it.toByte() }

  var computeTokenBindingResult: Result<String, ActionProofError> = Ok(defaultTokenBinding)
  var buildBindingsResult: Result<String, ActionProofError> = Ok("tb=$defaultTokenBinding")
  var buildPayloadResult: Result<ByteArray, ActionProofError> = Ok(defaultPayload)
  var createActionProofHeaderResult: Result<ActionProofHeader, ActionProofError>? = null

  val computeTokenBindingCalls = mutableListOf<Unit>()
  val buildBindingsCalls = mutableListOf<Pair<Map<String, String>, String?>>()
  val buildPayloadCalls = mutableListOf<BuildPayloadCall>()
  val createActionProofHeaderCalls = mutableListOf<Pair<List<String>, String?>>()

  data class BuildPayloadCall(
    val action: Action,
    val field: Field,
    val value: String?,
    val current: String?,
    val extra: Map<String, String>,
    val nonce: String?,
  )

  override suspend fun computeTokenBinding(): Result<String, ActionProofError> {
    computeTokenBindingCalls.add(Unit)
    return computeTokenBindingResult
  }

  override suspend fun buildBindings(
    extra: Map<String, String>,
    nonce: String?,
  ): Result<String, ActionProofError> {
    buildBindingsCalls.add(extra to nonce)
    return buildBindingsResult
  }

  override suspend fun buildPayload(
    action: Action,
    field: Field,
    value: String?,
    current: String?,
    extra: Map<String, String>,
    nonce: String?,
  ): Result<ByteArray, ActionProofError> {
    buildPayloadCalls.add(BuildPayloadCall(action, field, value, current, extra, nonce))
    return buildPayloadResult
  }

  override fun createActionProofHeader(
    signatures: List<String>,
    nonce: String?,
  ): Result<ActionProofHeader, ActionProofError> {
    createActionProofHeaderCalls.add(signatures to nonce)
    return createActionProofHeaderResult ?: Ok(
      ActionProofHeader(
        version = 1,
        signatures = signatures,
        nonce = nonce
      )
    )
  }

  fun reset() {
    computeTokenBindingResult = Ok(defaultTokenBinding)
    buildBindingsResult = Ok("tb=$defaultTokenBinding")
    buildPayloadResult = Ok(defaultPayload)
    createActionProofHeaderResult = null
    computeTokenBindingCalls.clear()
    buildBindingsCalls.clear()
    buildPayloadCalls.clear()
    createActionProofHeaderCalls.clear()
  }
}
