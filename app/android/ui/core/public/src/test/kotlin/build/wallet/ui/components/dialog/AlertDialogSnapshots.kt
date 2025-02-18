package build.wallet.ui.components.dialog

import build.wallet.kotest.paparazzi.paparazziExtension
import io.kotest.core.spec.style.FunSpec

class AlertDialogSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("alert dialog with primary button only") {
    paparazzi.snapshot {
      AlertDialogWithPrimaryButtonOnlyPreview()
    }
  }

  test("alert dialog with primary and secondary buttons") {
    paparazzi.snapshot {
      AlertWithPrimaryAndSecondaryButtonsPreview()
    }
  }

  test("alert dialog with primary destructive button") {
    paparazzi.snapshot {
      AlertDialogWithPrimaryDestructiveButtonPreview()
    }
  }

  test("alert with secondary destructive button") {
    paparazzi.snapshot {
      AlertWithSecondaryDestructiveButtonPreview()
    }
  }
})
