package build.wallet.ui.app.loading

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.LoadingBodyModel.Style.Implicit
import com.airbnb.lottie.LottieTask
import io.kotest.core.spec.style.FunSpec
import java.util.concurrent.Executor

class LoadingScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  beforeTest {
    // Needed for snapshotting the loading lottie animation
    LottieTask.EXECUTOR = Executor(Runnable::run)
  }

  test("loading screen without message") {
    paparazzi.snapshot {
      LoadingScreen(
        model = LoadingBodyModel(id = null)
      )
    }
  }

  test("loading screen with message") {
    paparazzi.snapshot {
      LoadingScreen(
        model =
          LoadingBodyModel(
            message = "Counting sheep...",
            id = null
          )
      )
    }
  }

  test("loading screen implicit") {
    paparazzi.snapshot {
      LoadingScreen(
        model =
          LoadingBodyModel(
            style = Implicit,
            id = null
          )
      )
    }
  }
})
