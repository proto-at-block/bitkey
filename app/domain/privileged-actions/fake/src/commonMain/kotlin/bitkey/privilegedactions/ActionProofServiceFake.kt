package bitkey.privilegedactions

import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.f8e.AccountId
import build.wallet.crypto.PublicKey
import build.wallet.f8e.actionproof.FormatValueRequest
import build.wallet.f8e.auth.ActionProofHeader
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import uniffi.actionproof.Action

class ActionProofServiceFake : ActionProofService {
  private val defaultTokenBinding =
    "59dc8eb2a2c5a8e3d0b7f4c6e1a9d2b5c8f0e3a6d9c2b5a8f1e4d7c0b3a6f9e2"
  private val defaultPayload = ByteArray(32) { it.toByte() }

  var computeTokenBindingResult: Result<String, ActionProofError> = Ok(defaultTokenBinding)
  var buildBindingsResult: Result<String, ActionProofError> = Ok("tb=$defaultTokenBinding")
  var buildPayloadResult: Result<ByteArray, ActionProofError> = Ok(defaultPayload)
  var createActionProofHeaderResult: Result<ActionProofHeader, ActionProofError>? = null
  var formatDisplayValueResult: Result<String, ActionProofError> = Ok("50.00 USD")
  var generateNonceResult: String = "a1"

  val computeTokenBindingCalls = mutableListOf<AccountId?>()
  val buildBindingsCalls = mutableListOf<BuildBindingsCall>()
  val buildPayloadCalls = mutableListOf<BuildPayloadCall>()
  val createActionProofHeaderCalls = mutableListOf<Pair<List<String>, String>>()
  val formatDisplayValueCalls = mutableListOf<FormatValueRequest>()

  data class BuildBindingsCall(
    val extra: Map<String, String>,
    val nonce: String,
    val accountId: AccountId?,
  )

  data class BuildPayloadCall(
    val action: Action,
    val value: String?,
    val extra: Map<String, String>,
    val nonce: String,
    val accountId: AccountId?,
  )

  override suspend fun computeTokenBinding(
    accountId: AccountId?,
  ): Result<String, ActionProofError> {
    computeTokenBindingCalls.add(accountId)
    return computeTokenBindingResult
  }

  override suspend fun buildBindings(
    extra: Map<String, String>,
    nonce: String,
    accountId: AccountId?,
  ): Result<String, ActionProofError> {
    buildBindingsCalls.add(BuildBindingsCall(extra, nonce, accountId))
    return buildBindingsResult
  }

  override suspend fun buildPayload(
    action: Action,
    value: String?,
    extra: Map<String, String>,
    nonce: String,
    accountId: AccountId?,
  ): Result<ByteArray, ActionProofError> {
    buildPayloadCalls.add(BuildPayloadCall(action, value, extra, nonce, accountId))
    return buildPayloadResult
  }

  override fun createActionProofHeader(
    signatures: List<String>,
    nonce: String,
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

  var buildAppSignedPayloadResult: Result<AppSignedActionProof, ActionProofError>? = null
  val buildAppSignedPayloadCalls = mutableListOf<BuildAppSignedPayloadCall>()

  data class BuildAppSignedPayloadCall(
    val action: Action,
    val value: String?,
    val extra: Map<String, String>,
    val appAuthKey: PublicKey<AppGlobalAuthKey>,
    val accountId: AccountId?,
  )

  private val defaultAppSignedPayload = AppSignedActionProof(
    bindings = "n=a1,tb=59dc8eb2a2c5a8e3d0b7f4c6e1a9d2b5c8f0e3a6d9c2b5a8f1e4d7c0b3a6f9e2",
    appSignature = "a".repeat(128),
    nonce = "a1"
  )

  override suspend fun buildAppSignedPayload(
    action: Action,
    value: String?,
    extra: Map<String, String>,
    appAuthKey: PublicKey<AppGlobalAuthKey>,
    accountId: AccountId?,
  ): Result<AppSignedActionProof, ActionProofError> {
    buildAppSignedPayloadCalls.add(BuildAppSignedPayloadCall(action, value, extra, appAuthKey, accountId))
    return buildAppSignedPayloadResult ?: Ok(defaultAppSignedPayload)
  }

  var createAppSignedHeaderResult: Result<ActionProofHeader, ActionProofError>? = null
  val createAppSignedHeaderCalls = mutableListOf<BuildAppSignedPayloadCall>()

  override suspend fun createAppSignedHeader(
    action: Action,
    value: String?,
    extra: Map<String, String>,
    appAuthKey: PublicKey<AppGlobalAuthKey>,
  ): Result<ActionProofHeader, ActionProofError> {
    createAppSignedHeaderCalls.add(BuildAppSignedPayloadCall(action, value, extra, appAuthKey, accountId = null))
    return createAppSignedHeaderResult ?: Ok(
      ActionProofHeader(
        version = 1,
        signatures = listOf("a".repeat(128)),
        nonce = "ab"
      )
    )
  }

  var cosignPayloadResult: Result<String, ActionProofError> = Ok("a".repeat(128))

  override suspend fun cosignPayload(
    action: Action,
    preBuiltBindings: String,
    appAuthKey: PublicKey<AppGlobalAuthKey>,
  ): Result<String, ActionProofError> = cosignPayloadResult

  override suspend fun formatDisplayValue(
    request: FormatValueRequest,
  ): Result<String, ActionProofError> {
    formatDisplayValueCalls.add(request)
    return formatDisplayValueResult
  }

  override fun generateNonce(): String = generateNonceResult

  fun reset() {
    computeTokenBindingResult = Ok(defaultTokenBinding)
    buildBindingsResult = Ok("tb=$defaultTokenBinding")
    buildPayloadResult = Ok(defaultPayload)
    createActionProofHeaderResult = null
    buildAppSignedPayloadResult = null
    createAppSignedHeaderResult = null
    formatDisplayValueResult = Ok("50.00 USD")
    computeTokenBindingCalls.clear()
    buildBindingsCalls.clear()
    buildPayloadCalls.clear()
    createActionProofHeaderCalls.clear()
    buildAppSignedPayloadCalls.clear()
    createAppSignedHeaderCalls.clear()
    formatDisplayValueCalls.clear()
    generateNonceResult = "a1"
  }
}
