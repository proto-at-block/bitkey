package build.wallet.ui.app.core

import build.wallet.kotest.paparazzi.paparazziExtension
import com.airbnb.lottie.LottieTask
import io.kotest.core.spec.style.FunSpec
import java.util.concurrent.Executor

class LoadingSuccessScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  beforeEach {
    // Needed for snapshotting the success lottie animation
    LottieTask.EXECUTOR = Executor(Runnable::run)
  }

  test("loading state") {
    paparazzi.snapshot {
      LoadingSuccessPreviewLoading()
    }
  }

  test("success state") {
    paparazzi.snapshot {
      LoadingSuccessPreviewSuccess()
    }
  }
})
