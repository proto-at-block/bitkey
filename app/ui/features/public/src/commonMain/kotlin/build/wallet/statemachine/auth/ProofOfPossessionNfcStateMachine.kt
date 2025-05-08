package build.wallet.statemachine.auth

import bitkey.auth.AccountAuthTokens
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.crypto.PublicKey
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.statemachine.core.AppSegment
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps.HardwareVerification
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps.HardwareVerification.Required

/**
 * TODO(W-3757): break down this state machine into more domain specific implementations.
 */
interface ProofOfPossessionNfcStateMachine : StateMachine<ProofOfPossessionNfcProps, ScreenModel>

/**
 * Specifies various types of Proof of Possession request.
 * The primary is HwKeyProof. The others are add-ons that batch
 * additional data with the HW Proof of Possession when doing so can
 * reduce the number of taps required in a given flow. If you're not sure
 * which one you need, then you need HwKeyProof.
 */
sealed interface Request {
  /**
   * Request to obtain hardware keyproof.
   */
  data class HwKeyProof(
    val onSuccess: (hwFactorProofOfPossession: HwFactorProofOfPossession) -> Unit,
  ) : Request

  /**
   * Request to obtain:
   * - hardware keyproof
   * - account ID signed with hardware
   * - app global auth key signed with hardware
   */
  data class HwKeyProofAndAccountSignature(
    val appAuthGlobalKey: PublicKey<AppGlobalAuthKey>,
    val accountId: FullAccountId,
    val onSuccess: (
      accountSignature: String,
      hwAuthPublicKey: HwAuthPublicKey,
      hwFactorProofOfPossession: HwFactorProofOfPossession,
      appGlobalAuthKeyHwSignature: AppGlobalAuthKeyHwSignature,
    ) -> Unit,
  ) : Request
}

/**
 * Props used to define behaviors specific to the Proof of Possession NFC state machine.
 *
 *
 * @property fullAccountId Account to be used to retrieve a refreshed access token.
 * @property appAuthKey app auth key to use for refreshing f8e access token.
 * When `null`, will use active keybox if any to get the key. Otherwise, will use this auth key's
 * corresponding private key (if present locally).
 * @property authTokens authentication access tokens to sign with hardware factor to obtain proof of
 * possession. If [authTokens] are not passed in, will attempt to use existing access tokens
 * from an active keybox or associated with [appAuthKey]. Passing [authTokens] in the case where
 * we have not app keys (e.g. during Lost App recovery) but we do have hardware factor which is
 * used to obtain access tokens.
 * @property screenPresentationStyle Define preferred presentation style of the default loading
 * screens.
 * @property onSuccess Called upon successful signing of the access token with the user's Bitkey
 * hardware wallet.
 * @property onBack Called when a user backs out of the NFC tap flow.
 * amounts.
 * @property onTokenRefresh When defined, allows consumers of the state machine to define a
 * `ScreenModel` to show while the auth token is refreshing. Else, we show a default
 * `LoadingScreenModel`
 * @property hardwareVerification: Whether to require the PoP to be paired with the app, and if so,
 * whether the PoP should be for the account's currently paired hardware or new hardware during recovery.
 * If you aren't in a recovery flow, just use the default.
 * @property onTokenRefreshError When defined, allows consumers of the state machine to define a
 * `ScreenModel` to show if auth token refreshing fails. Else, we show a default `ErrorFormScreenModel`
 */
data class ProofOfPossessionNfcProps(
  val request: Request,
  val fullAccountId: FullAccountId,
  val appAuthKey: PublicKey<AppGlobalAuthKey>? = null,
  val authTokens: AccountAuthTokens? = null,
  // TODO: BKR-1117: make non-nullable
  val segment: AppSegment? = null,
  val actionDescription: String? = null,
  val screenPresentationStyle: ScreenPresentationStyle,
  val onBack: () -> Unit,
  val onTokenRefresh: (() -> ScreenModel)? = null,
  val hardwareVerification: HardwareVerification = Required(),
  val onTokenRefreshError: (
    (
      isConnectivityError: Boolean,
      onRetry: () -> Unit,
    ) -> ScreenModel
  )? = null,
)
