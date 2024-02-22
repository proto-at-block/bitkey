package build.wallet.auth

import androidx.compose.runtime.Composable
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.keybox.Keybox
import build.wallet.f8e.auth.HwFactorProofOfPossession
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow

/**
 * Used to track the progress of a rotation request that you have initiated.
 * As a result, it's not available via a flow, but a return type of the composable
 * rotateAuthKeys function.
 */
public sealed class AuthKeyRotationRequestState {
  /**
   * The rotation request is in progress. This is the initial state of the request.
   */
  data object Rotating : AuthKeyRotationRequestState()

  /**
   * The rotation request has completed successfully. The new keybox is returned
   * along with a clearAttempt function to clear the attempt from the manager's state.
   * The clear attempt function will reset the locally persisted state of the rotation.
   */
  data class FinishedRotation(
    val rotatedKeybox: Keybox,
    val clearAttempt: () -> Unit,
  ) : AuthKeyRotationRequestState()

  /**
   * The rotation request has failed. The clearAttempt function clears the attempt from the
   * manager's state.
   */
  data class FailedRotation(val clearAttempt: () -> Unit) : AuthKeyRotationRequestState()
}

/**
 * Used to track the state of a rotation attempt that is in progress. This is available
 * via a flow, and is used to determine whether a rotation attempt is in progress.
 */
public sealed class AuthKeyRotationAttemptState {
  data object NoAttemptInProgress : AuthKeyRotationAttemptState()

  data object AttemptInProgress : AuthKeyRotationAttemptState()
}

interface AuthKeyRotationManager {
  @Composable
  fun startOrResumeAuthKeyRotation(
    hwFactorProofOfPossession: HwFactorProofOfPossession,
    keyboxToRotate: Keybox,
    rotateActiveKeybox: Boolean,
    hwAuthPublicKey: HwAuthPublicKey,
    hwSignedAccountId: String,
  ): AuthKeyRotationRequestState

  suspend fun getKeyRotationStatus(): Flow<Result<AuthKeyRotationAttemptState, Throwable>>
}
