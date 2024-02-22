package build.wallet.f8e.recovery

import build.wallet.bitkey.auth.AuthPublicKey
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.NetworkingError
import com.github.michaelbull.result.Result

interface InitiateHardwareAuthService {
  /**
   * Authenticate with f8e using hardware auth key to receive:
   * - f8e account associated with the hardware
   * - auth challenge and session which will be used to authorize initiation of recovery
   *
   * @param currentHardwareAuthKey - current [AuthPublicKey] of the hardware in customer's possession.
   */
  suspend fun start(
    f8eEnvironment: F8eEnvironment,
    currentHardwareAuthKey: HwAuthPublicKey,
  ): Result<AuthChallenge, NetworkingError>

  /**
   * A struct representing the expected return value when initiating a hardware authentication
   * session with f8e.
   *
   * @property fullAccountId account ID of the user authenticating
   * @property challenge string returned from Cognito, to be signed by hardware.
   * @property session uniquely identifies the authentication session. This is required when calling
   * used to verify the signature during hardware authentication.
   */
  data class AuthChallenge(
    val fullAccountId: FullAccountId,
    val challenge: String,
    val session: String,
  )
}
