package build.wallet.statemachine.export

import build.wallet.analytics.events.screen.id.ExportToolsEventTrackerScreenId
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel.ListGroup
import build.wallet.ui.model.icon.IconBackgroundType.Transient
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize.Small
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle.DIVIDER
import build.wallet.ui.model.list.ListItemAccessory.IconAccessory
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.CloseAccessory
import build.wallet.ui.model.toolbar.ToolbarMiddleAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel

data class ExportToolsSelectionModel(
  override val onBack: () -> Unit,
  val onExportTransactionHistoryClick: () -> Unit,
  val onExportDescriptorClick: () -> Unit,
) : FormBodyModel(
    onBack = onBack,
    toolbar = ToolbarModel(
      leadingAccessory = CloseAccessory(onBack),
      middleAccessory = ToolbarMiddleAccessoryModel(title = "Exports")
    ),
    mainContentList = immutableListOf(
      ListGroup(
        listGroupModel = ListGroupModel(
          items = immutableListOf(
            ListItemModel(
              title = "Transaction history",
              secondaryText = "Export CSV",
              trailingAccessory = IconAccessory(
                model = IconModel(
                  icon = Icon.SmallIconDownload,
                  iconSize = Small,
                  iconBackgroundType = Transient
                ),
                onClick = onExportTransactionHistoryClick
              ),
              onClick = onExportTransactionHistoryClick
            ),
            ListItemModel(
              title = "Current wallet descriptor",
              secondaryText = "Export XPUB bundle",
              trailingAccessory = IconAccessory(
                model = IconModel(
                  icon = Icon.SmallIconDownload,
                  iconSize = Small,
                  iconBackgroundType = Transient
                ),
                onClick = onExportDescriptorClick
              ),
              onClick = onExportDescriptorClick
            )
          ),
          style = DIVIDER
        )
      )
    ),
    header = null,
    primaryButton = null,
    id = ExportToolsEventTrackerScreenId.EXPORT_TOOLS_SCREEN
  )
