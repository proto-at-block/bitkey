package build.wallet.recovery.socrec

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class PostSocRecTaskRepositoryMock : PostSocRecTaskRepository {
  val mutableState = MutableStateFlow(PostSocialRecoveryTaskState.None)
  override val taskState: Flow<PostSocialRecoveryTaskState> = mutableState

  override suspend fun setHardwareReplacementNeeded(value: Boolean): Result<Unit, Error> {
    return Ok(Unit)
  }

  fun reset() {
    mutableState.value = PostSocialRecoveryTaskState.None
  }
}
