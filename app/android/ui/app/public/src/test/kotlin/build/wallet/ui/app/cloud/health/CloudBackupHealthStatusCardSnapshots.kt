package build.wallet.ui.app.cloud.health

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.cloud.health.CloudBackupHealthStatusCardType
import build.wallet.statemachine.core.Icon
import build.wallet.ui.app.backup.health.*
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Footer
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemModel
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
          backupStatus = ListItemModel(
            title = "Problem with Google account access",
            trailingAccessory = ListItemAccessory.IconAccessory(Icon.SmallIconWarning)
          )
        )
      )
    }
  }

  test("EEK good") {
    paparazzi.snapshot {
      CloudBackupHealthStatusCard(
        model =
          CloudBackupHealthStatusCardModelForPreview.copy(
            backupStatusActionButton = null
          )
      )
    }
  }

  test("EEK error") {
    paparazzi.snapshot {
      CloudBackupHealthStatusCard(
        model =
          CloudBackupHealthStatusCardModelForPreview.copy(
            backupStatusActionButton =
              ButtonModel(
                text = "Back up now",
                size = Footer,
                treatment = ButtonModel.Treatment.Primary,
                onClick = StandardClick {}
              ),
            backupStatus = ListItemModel(
              title = "Problem with Google account access",
              trailingAccessory = ListItemAccessory.IconAccessory(Icon.SmallIconWarning)
            ),
            type = CloudBackupHealthStatusCardType.EAK_BACKUP
          )
      )
    }
  }
})
