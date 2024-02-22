package build.wallet.ui.components.timer

import app.cash.paparazzi.DeviceConfig
import build.wallet.kotest.paparazzi.paparazziExtension
import io.kotest.core.spec.style.FunSpec

// TODO(W-876): add snapshot tests for states: some progress, timer finished (progress=1F).
//              Timer currently animates the progress when rendered, we need to capture final state.
class TimerSnapshots : FunSpec({
  val paparazzi = paparazziExtension(DeviceConfig.PIXEL_6)

  test("timer zero progress") {
    paparazzi.snapshot {
      TimerZeroProgressPreview()
    }
  }
})
