package build.wallet.statemachine.recovery.cloud

import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId
import build.wallet.platform.device.DevicePlatform
import build.wallet.statemachine.core.form.FormBodyModel

actual fun CloudBackupNotFoundBodyModel(
  onBack: () -> Unit,
  onCheckCloudAgain: () -> Unit,
  onCannotAccessCloud: () -> Unit,
  onImportEmergencyAccessKit: (() -> Unit)?,
  onShowTroubleshootingSteps: () -> Unit,
): FormBodyModel =
  CloudWarningBodyModel(
    devicePlatform = DevicePlatform.Android,
    id = CloudEventTrackerScreenId.CLOUD_BACKUP_NOT_FOUND,
    headerHeadline = "Wallet Not Found in Google Drive",
    headerSubline = "We’re unable to find a wallet connected to the Google Drive account you’re currently signed into.",
    onBack = onBack,
    onCheckCloudAgain = onCheckCloudAgain,
    onCannotAccessCloud = onCannotAccessCloud,
    onImportEmergencyAccessKit = onImportEmergencyAccessKit
  )

actual fun CloudNotSignedInBodyModel(
  onBack: () -> Unit,
  onCheckCloudAgain: () -> Unit,
  onCannotAccessCloud: () -> Unit,
  onImportEmergencyAccessKit: (() -> Unit)?,
  onShowTroubleshootingSteps: () -> Unit,
): FormBodyModel =
  CloudWarningBodyModel(
    devicePlatform = DevicePlatform.Android,
    id = CloudEventTrackerScreenId.CLOUD_NOT_SIGNED_IN,
    headerHeadline = "You’re not signed in to a Google Drive account",
    headerSubline = "To access your wallet, you’ll need to sign in to the Google Drive account you used when you setup your wallet.",
    onBack = onBack,
    onCheckCloudAgain = onCheckCloudAgain,
    onCannotAccessCloud = onCannotAccessCloud,
    onImportEmergencyAccessKit = onImportEmergencyAccessKit
  )
