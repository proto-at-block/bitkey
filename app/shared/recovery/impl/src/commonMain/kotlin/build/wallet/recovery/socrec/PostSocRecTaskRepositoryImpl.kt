package build.wallet.recovery.socrec

import build.wallet.db.DbError
import build.wallet.recovery.socrec.PostSocialRecoveryTaskState.HardwareReplacementNotification
import build.wallet.recovery.socrec.PostSocialRecoveryTaskState.HardwareReplacementScreens
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

class PostSocRecTaskRepositoryImpl(
  private val recoveryIncompleteDao: RecoveryIncompleteDao,
) : PostSocRecTaskRepository {
  /**
   * Stores an in-memory state of the recovery state to determine
   * if the user has started, but not yet completed a recovery in this
   * app session.
   */
  private val ongoingSocialRecoverySession = MutableStateFlow(false)

  override val taskState: Flow<PostSocialRecoveryTaskState> =
    combine(
      ongoingSocialRecoverySession,
      recoveryIncompleteDao.recoveryIncomplete()
    ) { ongoingSocialRecovery, recoveryIncomplete ->
      when {
        ongoingSocialRecovery -> HardwareReplacementScreens
        recoveryIncomplete -> HardwareReplacementNotification
        else -> PostSocialRecoveryTaskState.None
      }
    }

  override suspend fun setHardwareReplacementNeeded(value: Boolean): Result<Unit, DbError> {
    ongoingSocialRecoverySession.value = value
    return recoveryIncompleteDao.setRecoveryIncomplete(value)
  }
}
