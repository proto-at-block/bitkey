package build.wallet.statemachine.recovery.cloud

import build.wallet.cloud.backup.CloudBackup
import build.wallet.platform.device.DevicePlatform
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

interface ExistingFullAccountUiStateMachine : StateMachine<ExistingFullAccountUiProps, ScreenModel>

data class ExistingFullAccountUiProps(
  val cloudBackup: CloudBackup,
  val devicePlatform: DevicePlatform,
  val onBack: () -> Unit,
  val onRestore: () -> Unit,
  val onBackupArchive: () -> Unit,
)
