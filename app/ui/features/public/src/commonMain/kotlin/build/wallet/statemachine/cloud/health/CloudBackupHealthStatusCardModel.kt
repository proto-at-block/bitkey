package build.wallet.statemachine.cloud.health

import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.toolbar.ToolbarModel

enum class CloudBackupHealthStatusCardType {
  APP_KEY_BACKUP,
  EEK_BACKUP,
}

enum class CloudBackupHealthStatusTone {
  SUCCESS,
  WARNING,
  DANGER,
}

data class CloudBackupHealthStatusCardModel(
  val toolbarModel: ToolbarModel?,
  val headerModel: FormHeaderModel,
  val backupStatus: ListItemModel,
  val designSystemV2StatusText: String? = null,
  val designSystemV2StatusTone: CloudBackupHealthStatusTone? = null,
  val backupStatusActionButton: ButtonModel?,
  val type: CloudBackupHealthStatusCardType,
)
