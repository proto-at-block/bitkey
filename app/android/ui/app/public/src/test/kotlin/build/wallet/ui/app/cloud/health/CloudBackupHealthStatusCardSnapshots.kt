package build.wallet.ui.app.cloud.health

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.cloud.health.CloudBackupHealthStatusTone
import build.wallet.ui.app.backup.health.*
import io.kotest.core.spec.style.FunSpec

class CloudBackupHealthStatusCardSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("backup health good") {
    paparazzi.snapshot {
      CloudBackupHealthStatusCard(
        model =
          CloudBackupHealthStatusCardModelForPreview.copy(
            backupStatusActionButton = null,
            toolbarModel = null
          )
      )
    }
  }

  test("backup health error") {
    paparazzi.snapshot {
      CloudBackupHealthStatusCard(
        model = CloudBackupHealthStatusCardModelForPreview.copy(
          toolbarModel = null,
          backupStatus = CloudBackupHealthStatusProblemListItemForPreview,
          backupStatusActionButton = CloudBackupHealthStatusActionButtonForPreview
        )
      )
    }
  }

  test("backup health good with design system v2") {
    paparazzi.snapshot {
      CloudBackupHealthStatusCard(
        model =
          CloudBackupHealthStatusCardModelForPreview.copy(
            backupStatusActionButton = null,
            toolbarModel = null
          )
      )
    }
  }

  test("backup health error with design system v2") {
    paparazzi.snapshot {
      CloudBackupHealthStatusCard(
        model = CloudBackupHealthStatusCardModelForPreview.copy(
          toolbarModel = null,
          backupStatus = CloudBackupHealthStatusProblemListItemForPreview,
          designSystemV2StatusText = "No backup found",
          designSystemV2StatusTone = CloudBackupHealthStatusTone.DANGER,
          backupStatusActionButton = CloudBackupHealthStatusActionButtonForPreview
        )
      )
    }
  }

  test("EEK good") {
    paparazzi.snapshot {
      CloudBackupHealthStatusCard(
        model =
          CloudBackupHealthStatusCardEekModelForPreview.copy(
            backupStatusActionButton = null
          )
      )
    }
  }

  test("EEK error") {
    paparazzi.snapshot {
      CloudBackupHealthStatusCard(
        model =
          CloudBackupHealthStatusCardEekModelForPreview.copy(
            backupStatusActionButton = CloudBackupHealthStatusActionButtonForPreview,
            backupStatus = CloudBackupHealthStatusEekProblemListItemForPreview
          )
      )
    }
  }

  test("EEK good with design system v2") {
    paparazzi.snapshot {
      CloudBackupHealthStatusCard(
        model =
          CloudBackupHealthStatusCardEekModelForPreview.copy(
            backupStatusActionButton = null
          )
      )
    }
  }

  test("EEK error with design system v2") {
    paparazzi.snapshot {
      CloudBackupHealthStatusCard(
        model =
          CloudBackupHealthStatusCardEekModelForPreview.copy(
            designSystemV2StatusText = "No backup found",
            designSystemV2StatusTone = CloudBackupHealthStatusTone.DANGER,
            backupStatusActionButton = CloudBackupHealthStatusActionButtonForPreview,
            backupStatus = CloudBackupHealthStatusEekProblemListItemForPreview
          )
      )
    }
  }
})
