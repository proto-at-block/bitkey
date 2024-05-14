package build.wallet.ui.app.recovery

import build.wallet.asProgress
import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.recovery.inprogress.waiting.HardwareDelayNotifyInProgressScreenModel
import build.wallet.ui.app.core.form.FormScreen
import com.github.michaelbull.result.getOrThrow
import io.kotest.core.spec.style.FunSpec
import kotlin.time.Duration.Companion.days

class HardwareDelayNotifyInProgressScreenModelSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("hardware delay notify verification in progress screen") {
    paparazzi.snapshot {
      FormScreen(
        HardwareDelayNotifyInProgressScreenModel(
          onCancelRecovery = {},
          durationTitle = "2 days",
          progress = 0.3f.asProgress().getOrThrow(),
          remainingDelayPeriod = 2.days,
          onExit = {}
        )
      )
    }
  }
})
