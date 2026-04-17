package bitkey.privilegedactions

import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.f8e.AccountId
import build.wallet.crypto.PublicKey
import build.wallet.f8e.actionproof.FormatValueRequest
import build.wallet.f8e.auth.ActionProofHeader
import com.github.michaelbull.result.Result
import uniffi.actionproof.Action

/**
 * Intermediate result of app-signing an action proof payload.
 * Contains the pieces needed to either create a header directly (app-only)
 * or pass to hardware for co-signing (W3 composite NFC tap).
 */
data class AppSignedActionProof(
  val bindings: String,
  val appSignature: String,
  val nonce: String,
)

/**
 * Service for computing action proofs that bind privileged actions to authentication tokens.
 * Used to cryptographically link hardware-signed payloads to the user's current session.
 */
interface ActionProofService {
  companion object {
    const val ACTION_PROOF_VERSION: UInt = 1u
  }

  /**
   * Computes the token binding for the current active account's auth token.
   * If [accountId] is provided, resolves tokens for that account instead of using AccountService.
   * Returns a SHA256 hash binding the action proof to the user's JWT.
   */
  suspend fun computeTokenBinding(accountId: AccountId? = null): Result<String, ActionProofError>

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
    nonce: String,
    accountId: AccountId? = null,
  ): Result<String, ActionProofError>

  /**
   * Builds a canonical action payload for hardware signing.
   * Automatically computes and includes the token binding (tb) and nonce (n) if provided.
   *
   * @param action The action (e.g., ADD_RECOVERY_PHONE, SET_SPEND_WITHOUT_HARDWARE)
   * @param value Optional new value for the field
   * @param extra Additional context bindings (e.g., entity IDs)
   * @param nonce nonce for replay protection (added as "n" binding)
   * @return Binary payload ready for hardware signing
   */
  suspend fun buildPayload(
    action: Action,
    value: String? = null,
    extra: Map<String, String> = emptyMap(),
    nonce: String,
    accountId: AccountId? = null,
  ): Result<ByteArray, ActionProofError>

  /**
   * Creates an ActionProofHeader from hardware signatures.
   *
   * @param signatures List of 64-byte hex-encoded signatures (128 chars each)
   * @param nonce nonce for replay protection
   * @return ActionProofHeader ready for server submission
   */
  fun createActionProofHeader(
    signatures: List<String>,
    nonce: String,
  ): Result<ActionProofHeader, ActionProofError>

  /**
   * Generates a nonce, builds the payload and bindings, app-signs the payload,
   * and normalizes the DER signature to compact hex. Returns the intermediate
   * [AppSignedActionProof] containing bindings, appSignature, and nonce.
   *
   * Use this when you need the intermediate pieces (e.g., to pass bindings to
   * a W3 composite NFC command for hardware co-signing).
   *
   * @param action The action being authorized
   * @param value Optional new value for the field
   * @param extra Additional context bindings
   * @param appAuthKey The app global auth key used for signing
   * @return Intermediate proof with bindings, app signature, and nonce
   */
  suspend fun buildAppSignedPayload(
    action: Action,
    value: String? = null,
    extra: Map<String, String> = emptyMap(),
    appAuthKey: PublicKey<AppGlobalAuthKey>,
    accountId: AccountId? = null,
  ): Result<AppSignedActionProof, ActionProofError>

  /**
   * Builds an app-only signed action proof header in one shot.
   *
   * Delegates to [buildAppSignedPayload] then wraps the result in an [ActionProofHeader].
   * This is used for actions that need an app signature but no hardware signature
   * (e.g., initiating lost-hardware recovery on W3).
   *
   * @param action The action being authorized
   * @param value Optional new value for the field
   * @param extra Additional context bindings
   * @param appAuthKey The app global auth key used for signing
   * @return ActionProofHeader with a single app signature
   */
  suspend fun createAppSignedHeader(
    action: Action,
    value: String? = null,
    extra: Map<String, String> = emptyMap(),
    appAuthKey: PublicKey<AppGlobalAuthKey>,
  ): Result<ActionProofHeader, ActionProofError>

  /**
   * App-signs a canonical payload that was already signed by hardware.
   *
   * Used in W3 composite commands where the firmware signs the SAP payload during
   * an NFC tap, and the app must co-sign the same payload with its auth key.
   * The [preBuiltBindings] string must match exactly what the firmware signed.
   *
   * @param action The action being authorized
   * @param preBuiltBindings The full bindings string (already includes nonce + token binding)
   * @param appAuthKey The app global auth key used for signing
   * @return Compact hex signature (64 bytes, r||s)
   */
  suspend fun cosignPayload(
    action: Action,
    preBuiltBindings: String,
    appAuthKey: PublicKey<AppGlobalAuthKey>,
  ): Result<String, ActionProofError>

  /**
   * Formats a display value for an action proof via the server.
   * The server is the source of truth for formatting to prevent version skew.
   */
  suspend fun formatDisplayValue(request: FormatValueRequest): Result<String, ActionProofError>

  /**
   * Generates a random nonce for replay protection in action proofs.
   * Returns a 2-character lowercase hex string (e.g. "0a", "ff").
   */
  fun generateNonce(): String
}
