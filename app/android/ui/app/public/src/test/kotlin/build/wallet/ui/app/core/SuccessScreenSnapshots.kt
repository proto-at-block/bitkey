package build.wallet.ui.app.core

import build.wallet.kotest.paparazzi.paparazziExtension
import com.airbnb.lottie.LottieTask
import io.kotest.core.spec.style.FunSpec
import java.util.concurrent.Executor

class SuccessScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("success with subline screen") {
    paparazzi.snapshot {
      SuccessWithMessagePreview()
    }
  }

  test("success without subline screen") {
    paparazzi.snapshot {
      SuccessWithoutMessagePreview()
    }
  }

  test("success implicit") {
    // Needed for snapshotting the success lottie animation
    LottieTask.EXECUTOR = Executor(Runnable::run)
    paparazzi.snapshot {
      ImplicitSuccessPreview()
    }
  }
})
