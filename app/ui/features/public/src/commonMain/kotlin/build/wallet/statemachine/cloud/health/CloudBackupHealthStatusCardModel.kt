package build.wallet.statemachine.cloud.health

import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.toolbar.ToolbarModel

enum class CloudBackupHealthStatusCardType {
  MOBILE_KEY_BACKUP,
  EAK_BACKUP,
}

data class CloudBackupHealthStatusCardModel(
  val toolbarModel: ToolbarModel?,
  val headerModel: FormHeaderModel,
  val backupStatus: ListItemModel,
  val backupStatusActionButton: ButtonModel?,
  val type: CloudBackupHealthStatusCardType,
)
