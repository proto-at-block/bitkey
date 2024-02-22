package build.wallet.statemachine.recovery.cloud

import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId
import build.wallet.platform.device.DevicePlatform

actual fun CloudBackupNotFoundBodyModel(
  onBack: () -> Unit,
  onCheckCloudAgain: () -> Unit,
  onCannotAccessCloud: () -> Unit,
  onImportEmergencyAccessKit: (() -> Unit)?,
  onShowTroubleshootingSteps: () -> Unit,
) = CloudWarningBodyModel(
  devicePlatform = DevicePlatform.IOS,
  id = CloudEventTrackerScreenId.CLOUD_BACKUP_NOT_FOUND,
  headerHeadline = "Wallet Not Found in iCloud",
  headerSubline = "We’re unable to find a Bitkey backup in the iCloud account you’re currently signed in to.",
  onBack = onBack,
  onCheckCloudAgain = onShowTroubleshootingSteps,
  onCannotAccessCloud = onCannotAccessCloud,
  onImportEmergencyAccessKit = onImportEmergencyAccessKit
)

actual fun CloudNotSignedInBodyModel(
  onBack: () -> Unit,
  onCheckCloudAgain: () -> Unit,
  onCannotAccessCloud: () -> Unit,
  onImportEmergencyAccessKit: (() -> Unit)?,
  onShowTroubleshootingSteps: () -> Unit,
) = CloudWarningBodyModel(
  devicePlatform = DevicePlatform.IOS,
  id = CloudEventTrackerScreenId.CLOUD_NOT_SIGNED_IN,
  headerHeadline = "You’re not signed in to an iCloud account",
  headerSubline = "To access your wallet, you’ll need to sign in to the iCloud account you used when you setup your wallet.",
  onBack = onBack,
  onCheckCloudAgain = onShowTroubleshootingSteps,
  onCannotAccessCloud = onCannotAccessCloud,
  onImportEmergencyAccessKit = onImportEmergencyAccessKit
)
