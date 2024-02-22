package build.wallet.ui.app.recovery

import build.wallet.bitkey.factor.PhysicalFactor.App
import build.wallet.bitkey.factor.PhysicalFactor.Hardware
import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.recovery.conflict.model.ShowingSomeoneElseIsRecoveringBodyModel
import build.wallet.ui.app.core.form.FormScreen
import com.airbnb.lottie.LottieTask
import io.kotest.core.spec.style.FunSpec
import java.util.concurrent.Executor

class ShowingSomeoneElseIsRecoveringModelSnapshotTests : FunSpec({
  val paparazzi = paparazziExtension()

  beforeTest {
    // Needed for snapshotting the loading lottie animation
    LottieTask.EXECUTOR = Executor(Runnable::run)
  }

  test("showing someone else is recovering and customer canceling lost app - not loading") {
    paparazzi.snapshot {
      FormScreen(
        model =
          ShowingSomeoneElseIsRecoveringBodyModel(
            cancelingRecoveryLostFactor = App,
            isLoading = false,
            onCancelRecovery = {}
          )
      )
    }
  }

  test("showing someone else is recovering and customer canceling lost hardware - not loading") {
    paparazzi.snapshot {
      FormScreen(
        model =
          ShowingSomeoneElseIsRecoveringBodyModel(
            cancelingRecoveryLostFactor = Hardware,
            isLoading = false,
            onCancelRecovery = {}
          )
      )
    }
  }

  test("showing someone else is recovering and customer canceling lost app - loading") {
    paparazzi.snapshot {
      FormScreen(
        model =
          ShowingSomeoneElseIsRecoveringBodyModel(
            cancelingRecoveryLostFactor = App,
            isLoading = true,
            onCancelRecovery = {}
          )
      )
    }
  }

  test("showing someone else is recovering and customer canceling lost hardware - loading") {
    paparazzi.snapshot {
      FormScreen(
        model =
          ShowingSomeoneElseIsRecoveringBodyModel(
            cancelingRecoveryLostFactor = Hardware,
            isLoading = true,
            onCancelRecovery = {}
          )
      )
    }
  }
})
