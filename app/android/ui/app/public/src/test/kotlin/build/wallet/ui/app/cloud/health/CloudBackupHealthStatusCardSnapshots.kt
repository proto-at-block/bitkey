package build.wallet.ui.app.cloud.health

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.ui.app.backup.health.CloudBackupHealthStatusEAKError
import build.wallet.ui.app.backup.health.CloudBackupHealthStatusEAKGood
import build.wallet.ui.app.backup.health.CloudBackupHealthStatusError
import build.wallet.ui.app.backup.health.CloudBackupHealthStatusGood
import io.kotest.core.spec.style.FunSpec

class CloudBackupHealthStatusCardSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("backup health good") {
    paparazzi.snapshot {
      CloudBackupHealthStatusGood()
    }
  }

  test("backup health error") {
    paparazzi.snapshot {
      CloudBackupHealthStatusError()
    }
  }

  test("eak good") {
    paparazzi.snapshot {
      CloudBackupHealthStatusEAKGood()
    }
  }

  test("eak error") {
    paparazzi.snapshot {
      CloudBackupHealthStatusEAKError()
    }
  }
})
