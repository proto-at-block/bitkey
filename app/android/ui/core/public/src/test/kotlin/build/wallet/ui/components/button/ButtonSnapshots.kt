package build.wallet.ui.components.button

import app.cash.paparazzi.DeviceConfig
import build.wallet.kotest.paparazzi.paparazziExtension
import com.airbnb.lottie.LottieTask
import io.kotest.core.spec.style.FunSpec
import java.util.concurrent.Executor

class ButtonSnapshots : FunSpec(
  {
    val paparazzi = paparazziExtension(DeviceConfig.PIXEL_6.copy(screenHeight = 4000))

    test("buttons - regular size, with icon, enabled") {
      paparazzi.snapshot {
        RegularButtonsWithIconEnabled()
      }
    }

    test("button - regular size, without icon, enabled") {
      paparazzi.snapshot {
        RegularButtonsWithoutIconEnabled()
      }
    }

    test("button - compact size, with icon, enabled") {
      paparazzi.snapshot {
        CompactButtonsWithIconEnabled()
      }
    }

    test("button - compact size, without icon, enabled") {
      paparazzi.snapshot {
        CompactButtonsWithoutIconEnabled()
      }
    }

    test("button - footer size, with icon, enabled") {
      paparazzi.snapshot {
        FooterButtonsWithIconEnabled()
      }
    }

    test("button - footer size, without icon, enabled") {
      paparazzi.snapshot {
        FooterButtonsWithoutIconEnabled()
      }
    }

    test("button - elevated, enabled") {
      paparazzi.snapshot {
        ElevatedRegularButtonsEnabled()
      }
    }

    test("buttons - regular size, with icon, disabled") {
      paparazzi.snapshot {
        RegularButtonsWithIconDisabled()
      }
    }

    test("button - regular size, without icon, disabled") {
      paparazzi.snapshot {
        RegularButtonsWithoutIconDisabled()
      }
    }

    test("button - compact size, with icon, disabled") {
      paparazzi.snapshot {
        CompactButtonsWithIconDisabled()
      }
    }

    test("button - compact size, without icon, disabled") {
      paparazzi.snapshot {
        CompactButtonsWithoutIconDisabled()
      }
    }

    test("button - footer size, with icon, disabled") {
      paparazzi.snapshot {
        FooterButtonsWithIconDisabled()
      }
    }

    test("button - footer size, without icon, disabled") {
      paparazzi.snapshot {
        FooterButtonsWithoutIconDisabled()
      }
    }

    test("button - elevated, disabled") {
      paparazzi.snapshot {
        ElevatedRegularButtonsDisabled()
      }
    }

    test("button - loading") {
      // Needed for snapshotting the loading lottie animation
      LottieTask.EXECUTOR = Executor(Runnable::run)
      paparazzi.snapshot {
        RegularButtonLoading()
      }
    }

    test("floating button") {
      paparazzi.snapshot {
        FloatingButtonPreview()
      }
    }
  }
)
