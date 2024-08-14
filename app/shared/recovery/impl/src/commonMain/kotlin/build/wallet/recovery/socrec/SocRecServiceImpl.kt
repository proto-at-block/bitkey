package build.wallet.recovery.socrec

import build.wallet.recovery.socrec.PostSocialRecoveryTaskState.HardwareReplacementScreens
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SocRecServiceImpl(
  private val postSocRecTaskRepository: PostSocRecTaskRepository,
) : SocRecService {
  override fun justCompletedRecovery(): Flow<Boolean> {
    return postSocRecTaskRepository.taskState.map {
      it == HardwareReplacementScreens
    }
  }
}
