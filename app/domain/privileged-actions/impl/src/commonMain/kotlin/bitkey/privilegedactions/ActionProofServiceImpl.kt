package bitkey.privilegedactions

import bitkey.auth.AuthTokenScope
import build.wallet.account.AccountService
import build.wallet.auth.AuthTokensService
import build.wallet.catchingResult
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.auth.ActionProofHeader
import build.wallet.logging.logFailure
import build.wallet.logging.logWarn
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.mapError
import kotlinx.coroutines.flow.first
import uniffi.actionproof.Action
import uniffi.actionproof.ContextBinding
import uniffi.actionproof.ContextBindingPair
import uniffi.actionproof.Field
import uniffi.actionproof.computeTokenBinding
import uniffi.actionproof.contextBindingKey

@BitkeyInject(AppScope::class)
class ActionProofServiceImpl(
  private val accountService: AccountService,
  private val authTokensService: AuthTokensService,
) : ActionProofService {
  override suspend fun computeTokenBinding(): Result<String, ActionProofError> {
    val account = accountService.activeAccount().first()
    if (account == null) {
      logWarn { "computeTokenBinding: No active account" }
      return Err(ActionProofError.NoAuthToken)
    }

    val tokens = authTokensService.getTokens(
      accountId = account.accountId,
      scope = AuthTokenScope.Global
    ).getOrElse { return Err(ActionProofError.InternalError(it)) }

    if (tokens == null) {
      logWarn { "computeTokenBinding: No auth tokens for account ${account.accountId}" }
      return Err(ActionProofError.NoAuthToken)
    }

    return catchingResult { computeTokenBinding(tokens.accessToken.raw) }
      .logFailure { "FFI computeTokenBinding failed" }
      .mapError { ActionProofError.InternalError(it) }
  }

  private fun validateExtraBindings(extra: Map<String, String>): Result<Unit, ActionProofError> {
    val tbKey = contextBindingKey(ContextBinding.TOKEN_BINDING)
    val nonceKey = contextBindingKey(ContextBinding.NONCE)
    if (tbKey in extra) {
      return Err(ActionProofError.InvalidBindings("Token binding ($tbKey) is computed automatically"))
    }
    if (nonceKey in extra) {
      return Err(ActionProofError.InvalidBindings("Nonce ($nonceKey) should be passed via nonce parameter"))
    }
    return Ok(Unit)
  }

  override suspend fun buildBindings(
    extra: Map<String, String>,
    nonce: String?,
  ): Result<String, ActionProofError> {
    validateExtraBindings(extra).getOrElse { return Err(it) }

    val tokenBinding = computeTokenBinding().getOrElse { return Err(it) }
    val tbKey = contextBindingKey(ContextBinding.TOKEN_BINDING)
    val nonceKey = contextBindingKey(ContextBinding.NONCE)

    val allBindings = buildMap {
      putAll(extra)
      put(tbKey, tokenBinding)
      nonce?.let { put(nonceKey, it) }
    }

    return Ok(
      allBindings.entries
        .sortedBy { it.key }
        .joinToString(",") { "${it.key}=${it.value}" }
    )
  }

  override suspend fun buildPayload(
    action: Action,
    field: Field,
    value: String?,
    current: String?,
    extra: Map<String, String>,
    nonce: String?,
  ): Result<ByteArray, ActionProofError> {
    validateExtraBindings(extra).getOrElse { return Err(it) }

    val tokenBinding = computeTokenBinding().getOrElse { return Err(it) }
    val tbKey = contextBindingKey(ContextBinding.TOKEN_BINDING)
    val nonceKey = contextBindingKey(ContextBinding.NONCE)

    val bindings = extra.map { (k, v) -> ContextBindingPair(k, v) } +
      listOfNotNull(
        nonce?.let { ContextBindingPair(nonceKey, it) },
        ContextBindingPair(tbKey, tokenBinding)
      )

    return catchingResult {
      uniffi.actionproof.buildPayload(action, field, value, current, bindings)
        .map { it.toByte() }.toByteArray()
    }.logFailure { "FFI buildPayload failed for action=$action, field=$field" }
      .mapError { ActionProofError.InternalError(it) }
  }

  override fun createActionProofHeader(
    signatures: List<String>,
    nonce: String?,
  ): Result<ActionProofHeader, ActionProofError> {
    if (signatures.isEmpty()) {
      return Err(ActionProofError.InvalidSignature("Signatures list cannot be empty"))
    }
    val invalid = signatures.filterNot { it.matches(SIGNATURE_REGEX) }
    if (invalid.isNotEmpty()) {
      return Err(
        ActionProofError.InvalidSignature(
          "Invalid signature format: must be 130 lowercase hex characters"
        )
      )
    }
    return Ok(
      ActionProofHeader(
        signatures = signatures,
        nonce = nonce
      )
    )
  }

  private companion object {
    val SIGNATURE_REGEX = Regex("^[0-9a-f]{130}$")
  }
}
