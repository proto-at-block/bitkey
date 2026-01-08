package build.wallet.ui.app.recovery

import build.wallet.cloud.backup.CloudBackupV3WithFullAccountMock
import build.wallet.cloud.backup.CloudBackupV3WithLiteAccountMock
import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.platform.device.DevicePlatform.Android
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.recovery.cloud.CloudBackupFoundModel
import build.wallet.statemachine.recovery.cloud.CloudBackupItemModel
import build.wallet.statemachine.recovery.cloud.CloudBackupNotFoundBodyModel
import build.wallet.statemachine.recovery.cloud.CloudNotSignedInBodyModel
import build.wallet.statemachine.recovery.cloud.SelectCloudBackupBodyModel
import build.wallet.statemachine.recovery.cloud.SocialRecoveryExplanationModel
import build.wallet.ui.app.core.form.FormScreen
import io.kotest.core.spec.style.FunSpec
import kotlinx.collections.immutable.toImmutableList

class CloudRecoveryFormScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("cloud backup found screen") {
    paparazzi.snapshot {
      FormScreen(
        model =
          CloudBackupFoundModel(
            devicePlatform = Android,
            onBack = {},
            onRestore = {},
            onLostBitkeyClick = {},
            showSocRecButton = true
          )
      )
    }
  }

  test("cloud backup not found screen") {
    paparazzi.snapshot {
      FormScreen(
        model =
          CloudBackupNotFoundBodyModel(
            onBack = {},
            onCheckCloudAgain = {},
            onCannotAccessCloud = {},
            onImportEmergencyExitKit = {},
            onShowTroubleshootingSteps = {}
          )
      )
    }
  }

  test("cloud not signed in screen") {
    paparazzi.snapshot {
      FormScreen(
        model =
          CloudNotSignedInBodyModel(
            onBack = {},
            onCheckCloudAgain = {},
            onCannotAccessCloud = {},
            onImportEmergencyExitKit = {},
            onShowTroubleshootingSteps = {}
          )
      )
    }
  }

  test("social recovery explaination screen") {
    paparazzi.snapshot {
      FormScreen(
        model = SocialRecoveryExplanationModel(
          onBack = {},
          onContinue = {}
        )
      )
    }
  }

  test("cloud backup selection screen") {
    paparazzi.snapshot {
      FormScreen(
        model = SelectCloudBackupBodyModel(
          backupItems = listOf(
            CloudBackupItemModel(
              backup = CloudBackupV3WithFullAccountMock,
              displayLabel = "Joel's iPhone (2)",
              secondaryText = "Last backed up: 11/15/2025 at 6:30pm",
              icon = Icon.SmallIconBitkey
            ),
            CloudBackupItemModel(
              backup = CloudBackupV3WithFullAccountMock,
              displayLabel = "Joey's Pixel 9 Pro",
              secondaryText = "Last backed up: 11/14/2025 at 11:45am",
              icon = Icon.SmallIconBitkey
            ),
            CloudBackupItemModel(
              backup = CloudBackupV3WithLiteAccountMock,
              displayLabel = "Cameron's iPhone",
              secondaryText = "Recovery Contact Backup",
              icon = Icon.SmallIconShieldPerson
            )
          ).toImmutableList(),
          onBackupSelected = { _ -> },
          onBack = { },
          onLearnMoreClick = {}
        )
      )
    }
  }
})
