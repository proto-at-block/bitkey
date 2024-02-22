package build.wallet.recovery.socrec

import build.wallet.db.DbError
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class PostSocRecTaskRepositoryMock : PostSocRecTaskRepository {
  val mutableState = MutableStateFlow(PostSocialRecoveryTaskState.None)
  override val taskState: Flow<PostSocialRecoveryTaskState> = mutableState

  override suspend fun setHardwareReplacementNeeded(incomplete: Boolean): Result<Unit, DbError> {
    return Ok(Unit)
  }
}
