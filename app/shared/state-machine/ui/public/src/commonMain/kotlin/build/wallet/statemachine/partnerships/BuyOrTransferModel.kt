package build.wallet.statemachine.partnerships

import build.wallet.analytics.events.screen.id.DepositEventTrackerScreenId.PARTNERS_DEPOSIT_OPTIONS
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.SheetSize.MIN40
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel.ListGroup
import build.wallet.statemachine.core.form.RenderContext.Sheet
import build.wallet.ui.model.icon.IconTint.On30
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle.CARD_ITEM
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemModel

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
            secondaryText = "Using a debit card",
            onClick = onPurchase,
            trailingAccessory = ListItemAccessory.drillIcon(tint = On30)
          ),
          ListItemModel(
            title = "Transfer",
            secondaryText = "From a wallet or exchange",
            onClick = onTransfer,
            trailingAccessory = ListItemAccessory.drillIcon(tint = On30)
          )
        ),
      style = CARD_ITEM
    )
  return SheetModel(
    body =
      FormBodyModel(
        onBack = onBack,
        header =
          FormHeaderModel(
            headline = "How would you like to add bitcoin?",
            alignment = FormHeaderModel.Alignment.CENTER
          ),
        toolbar = null,
        mainContentList = immutableListOf(ListGroup(listGroupModel)),
        primaryButton = null,
        id = PARTNERS_DEPOSIT_OPTIONS,
        renderContext = Sheet
      ),
    size = MIN40,
    dragIndicatorVisible = true,
    onClosed = onBack
  )
}
