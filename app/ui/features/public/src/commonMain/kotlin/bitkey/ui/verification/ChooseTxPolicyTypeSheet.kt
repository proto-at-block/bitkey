package bitkey.ui.verification

import build.wallet.analytics.events.screen.id.TxVerificationEventTrackerScreenId
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.SheetSize
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.form.RenderContext
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemModel

/**
 * Bottom sheet shown when enabling a transaction verification policy.
 */
fun ChooseTxPolicyTypeSheet(
  onClose: () -> Unit,
  onAlwaysClick: () -> Unit,
  onAboveAmountClick: () -> Unit,
) = SheetModel(
  size = SheetSize.MIN40,
  onClosed = onClose,
  body = ChooseTxPolicyTypeSheetBody(
    onBack = onClose,
    onAlwaysClick = onAlwaysClick,
    onAboveAmountClick = onAboveAmountClick
  )
)

data class ChooseTxPolicyTypeSheetBody(
  override val onBack: () -> Unit,
  val onAlwaysClick: () -> Unit,
  val onAboveAmountClick: () -> Unit,
) : FormBodyModel(
    id = TxVerificationEventTrackerScreenId.CHOOSE_TX_POLICY_TYPE_SHEET,
    onBack = onBack,
    toolbar = null,
    header = FormHeaderModel(
      headline = "When do you want to verify an address?"
    ),
    mainContentList = immutableListOf(
      FormMainContentModel.ListGroup(
        ListGroupModel(
          items = immutableListOf(
            ListItemModel(
              title = "Above a set amount",
              trailingAccessory = ListItemAccessory.Companion.drillIcon(),
              onClick = onAboveAmountClick
            ),
            ListItemModel(
              title = "Always",
              trailingAccessory = ListItemAccessory.Companion.drillIcon(),
              onClick = onAlwaysClick
            )
          ),
          style = ListGroupStyle.NONE
        )
      )
    ),
    primaryButton = null,
    renderContext = RenderContext.Sheet
  )
