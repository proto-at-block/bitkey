package build.wallet.auth

import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.app.AppAuthPublicKeys
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.f8e.auth.HwFactorProofOfPossession
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow

sealed interface PendingAuthKeyRotationAttempt {
  data object ProposedAttempt : PendingAuthKeyRotationAttempt

  data class IncompleteAttempt(
    val newKeys: AppAuthPublicKeys,
  ) : PendingAuthKeyRotationAttempt
}

sealed interface AuthKeyRotationRequest {
  val newKeys: AppAuthPublicKeys

  data class Resume(
    override val newKeys: AppAuthPublicKeys,
  ) : AuthKeyRotationRequest

  data class Start(
    override val newKeys: AppAuthPublicKeys,
    val hwFactorProofOfPossession: HwFactorProofOfPossession,
    val hwAuthPublicKey: HwAuthPublicKey,
    val hwSignedAccountId: String,
  ) : AuthKeyRotationRequest
}

typealias AuthKeyRotationResult = Result<AuthKeyRotationSuccess, AuthKeyRotationFailure>

data class AuthKeyRotationSuccess(
  val onAcknowledge: () -> Unit,
)

sealed interface AuthKeyRotationFailure {
  // User lost their access, they need to go through recovery
  data class AccountLocked(
    val retryRequest: AuthKeyRotationRequest,
  ) : AuthKeyRotationFailure

  // Rotation didn't go through, but the old keys still work
  data class Acceptable(
    val onAcknowledge: () -> Unit,
  ) : AuthKeyRotationFailure

  // Rotation didn't go through, and we don't know what's happening, so better to be safe
  data class Unexpected(
    val retryRequest: AuthKeyRotationRequest,
  ) : AuthKeyRotationFailure
}

/**
 * Domain service for rotating auth keys for an active [FullAccount].
 */
interface FullAccountAuthKeyRotationService {
  suspend fun startOrResumeAuthKeyRotation(
    request: AuthKeyRotationRequest,
    account: FullAccount,
  ): AuthKeyRotationResult

  /**
   * The flow returned will usually only have a non-null value if there is a proposed rotation attempt,
   * or there was one in process that didn't complete. If a new rotation attempt is created,
   * for example from the settings screen, the flow will NOT emit a new value.
   */
  fun observePendingKeyRotationAttemptUntilNull(): Flow<PendingAuthKeyRotationAttempt?>

  suspend fun recommendKeyRotation()

  suspend fun dismissProposedRotationAttempt()
}
