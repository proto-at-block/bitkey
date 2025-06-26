package build.wallet.statemachine.recovery.cloud

import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId
import build.wallet.platform.device.DevicePlatform
import build.wallet.statemachine.core.form.FormBodyModel

actual fun CloudBackupNotFoundBodyModel(
  onBack: () -> Unit,
  onCheckCloudAgain: () -> Unit,
  onCannotAccessCloud: () -> Unit,
  onImportEmergencyExitKit: (() -> Unit)?,
  onShowTroubleshootingSteps: () -> Unit,
): FormBodyModel =
  CloudWarningBodyModel(
    devicePlatform = DevicePlatform.Jvm,
    id = CloudEventTrackerScreenId.CLOUD_BACKUP_NOT_FOUND,
    headerHeadline = "No wallet key found in this cloud account",
    headerSubline = "",
    onBack = onBack,
    onCheckCloudAgain = onCheckCloudAgain,
    onCannotAccessCloud = onCannotAccessCloud,
    onImportEmergencyExitKit = onImportEmergencyExitKit
  )

actual fun CloudNotSignedInBodyModel(
  onBack: () -> Unit,
  onCheckCloudAgain: () -> Unit,
  onCannotAccessCloud: () -> Unit,
  onImportEmergencyExitKit: (() -> Unit)?,
  onShowTroubleshootingSteps: () -> Unit,
): FormBodyModel =
  CloudWarningBodyModel(
    devicePlatform = DevicePlatform.Jvm,
    id = CloudEventTrackerScreenId.CLOUD_NOT_SIGNED_IN,
    headerHeadline = "You’re not signed in to cloud",
    headerSubline = "",
    onBack = onBack,
    onCheckCloudAgain = onCheckCloudAgain,
    onCannotAccessCloud = onCannotAccessCloud,
    onImportEmergencyExitKit = onImportEmergencyExitKit
  )
