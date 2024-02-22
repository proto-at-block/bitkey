package build.wallet.ui.app.cloud.health

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.ui.app.backup.health.CloudBackupHealthStatusCardWithButtonPreview
import build.wallet.ui.app.backup.health.CloudBackupHealthStatusCardWithoutButtonPreview
import io.kotest.core.spec.style.FunSpec

class CloudBackupHealthStatusCardSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("status card with action button") {
    paparazzi.snapshot {
      CloudBackupHealthStatusCardWithButtonPreview()
    }
  }

  test("status card without action button") {
    paparazzi.snapshot {
      CloudBackupHealthStatusCardWithoutButtonPreview()
    }
  }
})
