package build.wallet.recovery.socrec

import app.cash.turbine.test
import build.wallet.recovery.socrec.PostSocialRecoveryTaskState.HardwareReplacementNotification
import build.wallet.recovery.socrec.PostSocialRecoveryTaskState.HardwareReplacementScreens
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue

class SocRecServiceImplTests : FunSpec({
  val postSocRecTaskRepository = PostSocRecTaskRepositoryMock()
  val service = SocRecServiceImpl(postSocRecTaskRepository)

  beforeTest {
    postSocRecTaskRepository.reset()
  }

  test("justCompletedRecovery emits false by default") {
    service.justCompletedRecovery().test {
      awaitItem().shouldBeFalse()
    }
  }

  test("justCompletedRecovery emits true when post soc rec task state is HardwareReplacementScreens") {
    service.justCompletedRecovery().test {
      awaitItem().shouldBeFalse()

      postSocRecTaskRepository.mutableState.value = HardwareReplacementScreens

      awaitItem().shouldBeTrue()
    }
  }

  test("justCompletedRecovery emits false when post soc rec task state is not HardwareReplacementScreens") {
    postSocRecTaskRepository.mutableState.value = HardwareReplacementNotification

    service.justCompletedRecovery().test {
      awaitItem().shouldBeFalse()
    }
  }
})
