package build.wallet.statemachine.partnerships

import build.wallet.analytics.events.screen.id.DepositEventTrackerScreenId.PARTNERS_DEPOSIT_OPTIONS
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel.ListGroup
import build.wallet.statemachine.core.form.RenderContext.Sheet
import build.wallet.ui.model.icon.IconTint.On30
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle.CARD_ITEM
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.toolbar.ToolbarMiddleAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel

fun BuyOrTransferModel(
  onPurchase: () -> Unit,
  onTransfer: () -> Unit,
  onBack: () -> Unit,
): SheetModel {
  val listGroupModel =
    ListGroupModel(
      items =
        immutableListOf(
          ListItemModel(
            title = "Purchase",
            secondaryText = "New bitcoin with local currency",
            onClick = onPurchase,
            trailingAccessory = ListItemAccessory.drillIcon(tint = On30)
          ),
          ListItemModel(
            title = "Transfer",
            secondaryText = "Existing bitcoin from exchange",
            onClick = onTransfer,
            trailingAccessory = ListItemAccessory.drillIcon(tint = On30)
          )
        ),
      style = CARD_ITEM
    )
  return SheetModel(
    body = BuyOrTransferBodyModel(
      listGroupModel = listGroupModel,
      onPurchase = onPurchase,
      onTransfer = onTransfer,
      onBack = onBack
    ),
    dragIndicatorVisible = true,
    onClosed = onBack
  )
}

private data class BuyOrTransferBodyModel(
  val listGroupModel: ListGroupModel,
  val onPurchase: () -> Unit,
  val onTransfer: () -> Unit,
  override val onBack: () -> Unit,
) : FormBodyModel(
    onBack = onBack,
    header = null,
    toolbar =
      ToolbarModel(
        middleAccessory = ToolbarMiddleAccessoryModel(title = "Add bitcoin")
      ),
    mainContentList = immutableListOf(ListGroup(listGroupModel)),
    primaryButton = null,
    id = PARTNERS_DEPOSIT_OPTIONS,
    renderContext = Sheet
  )
