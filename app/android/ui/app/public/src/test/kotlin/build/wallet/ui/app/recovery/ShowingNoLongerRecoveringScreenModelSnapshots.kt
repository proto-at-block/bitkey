package build.wallet.ui.app.recovery

import build.wallet.bitkey.factor.PhysicalFactor.App
import build.wallet.bitkey.factor.PhysicalFactor.Hardware
import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.recovery.conflict.model.ShowingNoLongerRecoveringBodyModel
import build.wallet.ui.app.core.form.FormScreen
import com.airbnb.lottie.LottieTask
import io.kotest.core.spec.style.FunSpec
import java.util.concurrent.Executor

class ShowingNoLongerRecoveringScreenModelSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  beforeTest {
    // Needed for snapshotting the loading lottie animation
    LottieTask.EXECUTOR = Executor(Runnable::run)
  }

  test("showing no longer recovering as customer canceled lost app - not loading") {
    paparazzi.snapshot {
      FormScreen(
        model =
          ShowingNoLongerRecoveringBodyModel(
            canceledRecoveringFactor = App,
            isLoading = false,
            onAcknowledge = {}
          )
      )
    }
  }

  test("showing no longer recovering as customer canceled lost hardware - not loading") {
    paparazzi.snapshot {
      FormScreen(
        model =
          ShowingNoLongerRecoveringBodyModel(
            canceledRecoveringFactor = Hardware,
            isLoading = false,
            onAcknowledge = {}
          )
      )
    }
  }

  test("showing no longer recovering as customer canceled lost app - loading") {
    paparazzi.snapshot {
      FormScreen(
        model =
          ShowingNoLongerRecoveringBodyModel(
            canceledRecoveringFactor = App,
            isLoading = true,
            onAcknowledge = {}
          )
      )
    }
  }

  test("showing no longer recovering as customer canceled lost hardware - loading") {
    paparazzi.snapshot {
      FormScreen(
        model =
          ShowingNoLongerRecoveringBodyModel(
            canceledRecoveringFactor = Hardware,
            isLoading = true,
            onAcknowledge = {}
          )
      )
    }
  }
})
