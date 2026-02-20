package build.wallet.f8e.onboarding

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.NetworkingError
import build.wallet.ktor.result.RedactedResponseBody
import com.github.michaelbull.result.Result
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

interface OnboardingF8eClient {
  /**
   * Called to notify the server that onboarding has been completed.
   *
   * When the server receives this call, it will start requiring [HwProofOfPossession] for
   * certain protected customer actions (like setting up notifications). This allows actions
   * taken during onboarding (before this call is made) to be performed without needing to
   * provide [HwProofOfPossession] (for a better customer experience).
   */
  suspend fun completeOnboarding(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
  ): Result<Unit, NetworkingError>

  /**
   * V2 of completeOnboarding endpoint.
   *
   * Called to notify the server that onboarding has been completed. Returns the active auth
   * and spending public keys along with a signature from the server attesting to them.
   *
   * When the server receives this call, it will start requiring [HwProofOfPossession] for
   * certain protected customer actions (like setting up notifications). This allows actions
   * taken during onboarding (before this call is made) to be performed without needing to
   * provide [HwProofOfPossession] (for a better customer experience).
   */
  suspend fun completeOnboardingV2(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
  ): Result<CompleteOnboardingResponseV2, NetworkingError>
}

/**
 * Response from the V2 complete onboarding endpoint containing the active auth and spending
 * keys along with a server signature attesting to them.
 *
 * @property appAuthPub App authentication public key
 * @property hardwareAuthPub Hardware authentication public key
 * @property appSpendingPub App spending public key
 * @property hardwareSpendingPub Hardware spending public key
 * @property serverSpendingPub Server spending public key
 * @property signature Server signature over all 5 public keys
 */
@Serializable
data class CompleteOnboardingResponseV2(
  @SerialName("app_auth_pub")
  val appAuthPub: String,
  @SerialName("hardware_auth_pub")
  val hardwareAuthPub: String,
  @SerialName("app_spending_pub")
  val appSpendingPub: String,
  @SerialName("hardware_spending_pub")
  val hardwareSpendingPub: String,
  @SerialName("server_spending_pub")
  val serverSpendingPub: String,
  @SerialName("signature")
  val signature: String,
) : RedactedResponseBody
