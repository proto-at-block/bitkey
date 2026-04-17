package bitkey.privilegedactions

import bitkey.auth.AuthTokenScope
import build.wallet.account.AccountService
import build.wallet.account.getActiveOrOnboardingAccountOrNull
import build.wallet.auth.AppAuthKeyMessageSigner
import build.wallet.auth.AuthTokensService
import build.wallet.bitkey.account.Account
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.f8e.AccountId
import build.wallet.catchingResult
import build.wallet.crypto.PublicKey
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.encrypt.SignatureUtils
import build.wallet.f8e.actionproof.ActionProofFormatF8eClient
import build.wallet.f8e.actionproof.FormatValueRequest
import build.wallet.f8e.auth.ActionProofHeader
import build.wallet.logging.logFailure
import build.wallet.logging.logWarn
import com.github.michaelbull.result.*
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.toByteString
import uniffi.actionproof.Action
import uniffi.actionproof.ContextBinding
import uniffi.actionproof.ContextBindingPair
import kotlin.random.Random

@BitkeyInject(AppScope::class)
class ActionProofServiceImpl(
  private val accountService: AccountService,
  private val authTokensService: AuthTokensService,
  private val ffiProvider: ActionProofFfiProvider,
  private val actionProofFormatF8eClient: ActionProofFormatF8eClient,
  private val appAuthKeyMessageSigner: AppAuthKeyMessageSigner,
  private val signatureUtils: SignatureUtils,
) : ActionProofService {
  // Note: If the access token is near expiry (~10s) when this is called, and the
  // subsequent HTTP request triggers a token refresh, the ActionProof may fail
  // server-side verification (the binding was computed with the old token). This is
  // an edge case with a small window; the failure mode is a retryable 403 error.
  override suspend fun computeTokenBinding(
    accountId: AccountId?,
  ): Result<String, ActionProofError> {
    val resolvedAccountId = resolveAccountId(accountId).getOrElse { return Err(it) }
    val tokens = authTokensService.getTokens(
      accountId = resolvedAccountId,
      scope = AuthTokenScope.Global
    ).getOrElse { return Err(ActionProofError.InternalError(it)) }

    if (tokens == null) {
      logWarn { "computeTokenBinding: No auth tokens for account $resolvedAccountId" }
      return Err(ActionProofError.NoAuthToken)
    }

    return catchingResult { ffiProvider.computeTokenBinding(tokens.accessToken.raw) }
      .logFailure { "FFI computeTokenBinding failed" }
      .mapError { ActionProofError.InternalError(it) }
  }

  private fun validateExtraBindings(extra: Map<String, String>): Result<Unit, ActionProofError> {
    val tbKey = ffiProvider.contextBindingKey(ContextBinding.TOKEN_BINDING)
    val nonceKey = ffiProvider.contextBindingKey(ContextBinding.NONCE)
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
    nonce: String,
    accountId: AccountId?,
  ): Result<String, ActionProofError> {
    validateExtraBindings(extra).getOrElse { return Err(it) }
    val tokenBinding = computeTokenBinding(accountId).getOrElse { return Err(it) }
    return Ok(buildBindingsString(extra, nonce, tokenBinding))
  }

  override suspend fun buildPayload(
    action: Action,
    value: String?,
    extra: Map<String, String>,
    nonce: String,
    accountId: AccountId?,
  ): Result<ByteArray, ActionProofError> {
    validateExtraBindings(extra).getOrElse { return Err(it) }
    val tokenBinding = computeTokenBinding(accountId).getOrElse { return Err(it) }
    return buildPayloadBytes(action, value, extra, nonce, tokenBinding)
  }

  private fun buildBindingsString(
    extra: Map<String, String>,
    nonce: String,
    tokenBinding: String,
  ): String {
    val tbKey = ffiProvider.contextBindingKey(ContextBinding.TOKEN_BINDING)
    val nonceKey = ffiProvider.contextBindingKey(ContextBinding.NONCE)

    val allBindings = buildMap {
      putAll(extra)
      put(tbKey, tokenBinding)
      put(nonceKey, nonce)
    }

    return allBindings.entries
      .sortedBy { it.key }
      .joinToString(",") { "${it.key}=${it.value}" }
  }

  private fun buildPayloadBytes(
    action: Action,
    value: String?,
    extra: Map<String, String>,
    nonce: String,
    tokenBinding: String,
  ): Result<ByteArray, ActionProofError> {
    val tbKey = ffiProvider.contextBindingKey(ContextBinding.TOKEN_BINDING)
    val nonceKey = ffiProvider.contextBindingKey(ContextBinding.NONCE)

    val bindings = extra.map { (k, v) -> ContextBindingPair(k, v) } +
      listOf(
        ContextBindingPair(nonceKey, nonce),
        ContextBindingPair(tbKey, tokenBinding)
      )

    return catchingResult {
      ffiProvider.buildPayload(action, value, bindings)
        .map { it.toByte() }.toByteArray()
    }.logFailure { "FFI buildPayload failed for action=$action" }
      .mapError { ActionProofError.InternalError(it) }
  }

  override fun createActionProofHeader(
    signatures: List<String>,
    nonce: String,
  ): Result<ActionProofHeader, ActionProofError> {
    if (signatures.isEmpty()) {
      return Err(ActionProofError.InvalidSignature("Signatures list cannot be empty"))
    }
    val invalid = signatures.filterNot { it.matches(SIGNATURE_REGEX) }
    if (invalid.isNotEmpty()) {
      return Err(
        ActionProofError.InvalidSignature(
          "Invalid signature format: must be 128 lowercase hex characters"
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

  override suspend fun buildAppSignedPayload(
    action: Action,
    value: String?,
    extra: Map<String, String>,
    appAuthKey: PublicKey<AppGlobalAuthKey>,
    accountId: AccountId?,
  ): Result<AppSignedActionProof, ActionProofError> {
    validateExtraBindings(extra).getOrElse { return Err(it) }

    val nonce = generateNonce()
    val tokenBinding = computeTokenBinding(accountId).getOrElse { return Err(it) }

    val payload = buildPayloadBytes(action, value, extra, nonce, tokenBinding)
      .getOrElse { return Err(it) }
    val bindings = buildBindingsString(extra, nonce, tokenBinding)

    val appSignatureDer = appAuthKeyMessageSigner.signMessage(
      publicKey = appAuthKey,
      message = payload.toByteString()
    ).getOrElse { return Err(ActionProofError.InternalError(it)) }

    val appSignature = catchingResult {
      signatureUtils.decodeSignatureFromDer(appSignatureDer.lowercase().decodeHex())
    }.map { it.toByteString().hex() }
      .getOrElse { return Err(ActionProofError.InternalError(it)) }

    return Ok(AppSignedActionProof(bindings = bindings, appSignature = appSignature, nonce = nonce))
  }

  override suspend fun createAppSignedHeader(
    action: Action,
    value: String?,
    extra: Map<String, String>,
    appAuthKey: PublicKey<AppGlobalAuthKey>,
  ): Result<ActionProofHeader, ActionProofError> {
    val signed = buildAppSignedPayload(action, value, extra, appAuthKey)
      .getOrElse { return Err(it) }

    return createActionProofHeader(
      signatures = listOf(signed.appSignature),
      nonce = signed.nonce
    )
  }

  override suspend fun cosignPayload(
    action: Action,
    preBuiltBindings: String,
    appAuthKey: PublicKey<AppGlobalAuthKey>,
  ): Result<String, ActionProofError> {
    // Parse the pre-built bindings string (key=value,key=value,...) into pairs safely
    if (preBuiltBindings.isBlank()) {
      return Err(ActionProofError.InvalidBindings("Bindings string is blank"))
    }
    val bindingPairs = mutableListOf<ContextBindingPair>()
    for (rawPair in preBuiltBindings.split(",")) {
      val pair = rawPair.trim()
      if (pair.isEmpty()) {
        return Err(ActionProofError.InvalidBindings("Empty binding pair"))
      }
      val parts = pair.split("=", limit = 2)
      if (parts.size != 2 || parts[0].isEmpty()) {
        return Err(ActionProofError.InvalidBindings("Malformed binding pair: $pair"))
      }
      bindingPairs += ContextBindingPair(parts[0], parts[1])
    }

    // Build the canonical payload using the same FFI as the firmware
    val payload = catchingResult {
      ffiProvider.buildPayload(action, null, bindingPairs)
        .map { it.toByte() }.toByteArray()
    }.getOrElse { return Err(ActionProofError.InternalError(it)) }

    val appSignatureDer = appAuthKeyMessageSigner.signMessage(
      publicKey = appAuthKey,
      message = payload.toByteString()
    ).getOrElse { return Err(ActionProofError.InternalError(it)) }

    val appSignature = catchingResult {
      signatureUtils.decodeSignatureFromDer(appSignatureDer.lowercase().decodeHex())
    }.map { it.toByteString().hex() }
      .getOrElse { return Err(ActionProofError.InternalError(it)) }

    return Ok(appSignature)
  }

  override suspend fun formatDisplayValue(
    request: FormatValueRequest,
  ): Result<String, ActionProofError> {
    val account = accountService.getActiveOrOnboardingAccountOrNull<Account>().get()
      ?: return Err(ActionProofError.NoAccount)
    return actionProofFormatF8eClient.formatValue(
      f8eEnvironment = account.config.f8eEnvironment,
      accountId = account.accountId,
      request = request
    ).mapError { ActionProofError.F8eError(it) }
  }

  override fun generateNonce(): String = Random.nextInt(256).toString(16).padStart(2, '0')

  private suspend fun resolveAccountId(
    accountId: AccountId?,
  ): Result<AccountId, ActionProofError> {
    accountId?.let { return Ok(it) }

    val account = accountService.getActiveOrOnboardingAccountOrNull<Account>().get()
    if (account == null) {
      logWarn { "computeTokenBinding: No active account" }
      return Err(ActionProofError.NoAccount)
    }

    return Ok(account.accountId)
  }

  private companion object {
    val SIGNATURE_REGEX = Regex("^[0-9a-f]{128}$")
  }
}
