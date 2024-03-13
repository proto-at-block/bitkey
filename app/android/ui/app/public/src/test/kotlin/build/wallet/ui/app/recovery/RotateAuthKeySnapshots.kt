package build.wallet.ui.app.recovery

import build.wallet.analytics.events.screen.context.AuthKeyRotationEventTrackerScreenIdContext
import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.recovery.cloud.RotateAuthKeyScreens
import build.wallet.ui.app.core.LoadingSuccessScreen
import build.wallet.ui.app.core.form.FormScreen
import com.airbnb.lottie.LottieTask
import io.kotest.core.spec.style.FunSpec
import java.util.concurrent.Executor

class RotateAuthKeySnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  beforeTest {
    // Needed for snapshotting the loading lottie animation
    LottieTask.EXECUTOR = Executor(Runnable::run)
  }

  test("auth key rotation choice - proposed rotation") {
    paparazzi.snapshot {
      FormScreen(
        RotateAuthKeyScreens.DeactivateDevicesAfterRestoreChoice(
          onNotRightNow = {},
          removeAllOtherDevicesEnabled = false,
          onRemoveAllOtherDevices = {}
        )
      )
    }
  }

  test("auth key rotation choice - settings") {
    paparazzi.snapshot {
      FormScreen(
        RotateAuthKeyScreens.DeactivateDevicesFromSettingsChoice(
          onBack = {},
          removeAllOtherDevicesEnabled = false,
          onRemoveAllOtherDevices = {}
        )
      )
    }
  }

  test("auth key rotation loading - proposed rotation") {
    paparazzi.snapshot {
      LoadingSuccessScreen(
        RotateAuthKeyScreens.RotatingKeys(
          context = AuthKeyRotationEventTrackerScreenIdContext.PROPOSED_ROTATION
        )
      )
    }
  }

  test("auth key rotation loading - settings") {
    paparazzi.snapshot {
      LoadingSuccessScreen(
        RotateAuthKeyScreens.RotatingKeys(
          context = AuthKeyRotationEventTrackerScreenIdContext.SETTINGS
        )
      )
    }
  }

  test("auth key rotation loading - failed attempt") {
    paparazzi.snapshot {
      LoadingSuccessScreen(
        RotateAuthKeyScreens.RotatingKeys(
          context = AuthKeyRotationEventTrackerScreenIdContext.FAILED_ATTEMPT
        )
      )
    }
  }

  test("auth key rotation success - proposed rotation") {
    paparazzi.snapshot {
      FormScreen(
        RotateAuthKeyScreens.Confirmation(
          context = AuthKeyRotationEventTrackerScreenIdContext.PROPOSED_ROTATION,
          onSelected = {}
        )
      )
    }
  }

  test("auth key rotation success - settings") {
    paparazzi.snapshot {
      FormScreen(
        RotateAuthKeyScreens.Confirmation(
          context = AuthKeyRotationEventTrackerScreenIdContext.SETTINGS,
          onSelected = {}
        )
      )
    }
  }

  test("auth key rotation success - failed attempt") {
    paparazzi.snapshot {
      FormScreen(
        RotateAuthKeyScreens.Confirmation(
          context = AuthKeyRotationEventTrackerScreenIdContext.FAILED_ATTEMPT,
          onSelected = {}
        )
      )
    }
  }

  test("auth key rotation acceptable failure - proposed rotation") {
    paparazzi.snapshot {
      FormScreen(
        RotateAuthKeyScreens.AcceptableFailure(
          context = AuthKeyRotationEventTrackerScreenIdContext.PROPOSED_ROTATION,
          onRetry = {},
          onAcknowledge = {}
        )
      )
    }
  }

  test("auth key rotation acceptable failure - settings") {
    paparazzi.snapshot {
      FormScreen(
        RotateAuthKeyScreens.AcceptableFailure(
          context = AuthKeyRotationEventTrackerScreenIdContext.SETTINGS,
          onRetry = {},
          onAcknowledge = {}
        )
      )
    }
  }

  test("auth key rotation acceptable failure - failed attempt") {
    paparazzi.snapshot {
      FormScreen(
        RotateAuthKeyScreens.AcceptableFailure(
          context = AuthKeyRotationEventTrackerScreenIdContext.FAILED_ATTEMPT,
          onRetry = {},
          onAcknowledge = {}
        )
      )
    }
  }

  test("auth key rotation unexpected failure - proposed rotation") {
    paparazzi.snapshot {
      FormScreen(
        RotateAuthKeyScreens.UnexpectedFailure(
          context = AuthKeyRotationEventTrackerScreenIdContext.PROPOSED_ROTATION,
          onRetry = {},
          onContactSupport = {}
        )
      )
    }
  }

  test("auth key rotation unexpected failure - settings") {
    paparazzi.snapshot {
      FormScreen(
        RotateAuthKeyScreens.UnexpectedFailure(
          context = AuthKeyRotationEventTrackerScreenIdContext.SETTINGS,
          onRetry = {},
          onContactSupport = {}
        )
      )
    }
  }

  test("auth key rotation unexpected failure - failed attempt") {
    paparazzi.snapshot {
      FormScreen(
        RotateAuthKeyScreens.UnexpectedFailure(
          context = AuthKeyRotationEventTrackerScreenIdContext.FAILED_ATTEMPT,
          onRetry = {},
          onContactSupport = {}
        )
      )
    }
  }

  test("auth key rotation account locked failure - proposed rotation") {
    paparazzi.snapshot {
      FormScreen(
        RotateAuthKeyScreens.AccountLockedFailure(
          context = AuthKeyRotationEventTrackerScreenIdContext.PROPOSED_ROTATION,
          onRetry = {},
          onContactSupport = {}
        )
      )
    }
  }

  test("auth key rotation account locked failure - settings") {
    paparazzi.snapshot {
      FormScreen(
        RotateAuthKeyScreens.AccountLockedFailure(
          context = AuthKeyRotationEventTrackerScreenIdContext.SETTINGS,
          onRetry = {},
          onContactSupport = {}
        )
      )
    }
  }

  test("auth key rotation account locked failure - failed attempt") {
    paparazzi.snapshot {
      FormScreen(
        RotateAuthKeyScreens.AccountLockedFailure(
          context = AuthKeyRotationEventTrackerScreenIdContext.FAILED_ATTEMPT,
          onRetry = {},
          onContactSupport = {}
        )
      )
    }
  }

  test("auth key rotation dismissing proposal") {
    paparazzi.snapshot {
      LoadingSuccessScreen(
        RotateAuthKeyScreens.DismissingProposal(
          context = AuthKeyRotationEventTrackerScreenIdContext.PROPOSED_ROTATION
        )
      )
    }
  }
})
