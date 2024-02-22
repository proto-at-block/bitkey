package build.wallet.recovery.socrec

import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow

/**
 * Repository for accessing the incomplete state of a social recovery flow.
 */
interface PostSocRecTaskRepository {
  /**
   * Flow with the current status of tasks required to complete the social recovery process.
   */
  val taskState: Flow<PostSocialRecoveryTaskState>

  /**
   * Sets a follow-up task for replacing hardware to complete SocRec.
   */
  suspend fun setHardwareReplacementNeeded(value: Boolean): Result<Unit, Error>
}
