package build.wallet.ui.app.cloud.health

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.ui.app.backup.health.CloudBackupHealthDashboardScreenDesignSystemV2Preview
import build.wallet.ui.app.backup.health.CloudBackupHealthDashboardScreenPreview
import io.kotest.core.spec.style.FunSpec

class CloudBackupHealthDashboardScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("cloud backup health screen") {
    paparazzi.snapshot {
      CloudBackupHealthDashboardScreenPreview()
    }
  }

  test("cloud backup health screen with design system v2") {
    paparazzi.snapshot {
      CloudBackupHealthDashboardScreenDesignSystemV2Preview()
    }
  }
})
