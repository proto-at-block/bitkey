package build.wallet.statemachine.send.utxo

import build.wallet.analytics.events.screen.id.SendEventTrackerScreenId
import build.wallet.compose.collections.buildImmutableList
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel.ListGroup
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Footer
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupModel.HeaderTreatment.PRIMARY
import build.wallet.ui.model.list.ListGroupStyle.CARD_GROUP
import build.wallet.ui.model.list.ListItemAccessory.CheckboxAccessory
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.list.ListItemSideTextTint
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarMiddleAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

data class UtxoSelectionBodyModel(
  override val onBack: () -> Unit,
  val utxoItems: ImmutableList<UtxoSelectionListItem>,
  val headerSubline: String?,
  val confirmEnabled: Boolean,
  val onConfirm: () -> Unit,
  val onClear: () -> Unit,
  val showClear: Boolean,
) : FormBodyModel(
    id = SendEventTrackerScreenId.SEND_UTXO_SELECTION,
    onBack = onBack,
    toolbar = ToolbarModel(
      leadingAccessory = BackAccessory(onClick = onBack),
      middleAccessory = ToolbarMiddleAccessoryModel(title = "Choose coins")
    ),
    header = FormHeaderModel(
      headline = "Choose coins",
      subline = headerSubline
    ),
    mainContentList = buildImmutableList {
      if (utxoItems.isNotEmpty()) {
        ListGroup(
          listGroupModel = ListGroupModel(
            header = "Confirmed UTXOs",
            items = utxoItems.map { item ->
              ListItemModel(
                title = item.valueLabel,
                sideText = item.outpointLabel,
                sideTextTint = ListItemSideTextTint.SECONDARY,
                leadingAccessory = CheckboxAccessory(
                  isChecked = item.isSelected,
                  onClick = item.onToggle
                ),
                onClick = item.onToggle
              )
            }.toImmutableList(),
            style = CARD_GROUP,
            headerTreatment = PRIMARY
          )
        ).also(::add)
      }
    },
    primaryButton = ButtonModel(
      text = "Confirm",
      isEnabled = confirmEnabled,
      size = Footer,
      onClick = StandardClick(onConfirm)
    ),
    secondaryButton = if (showClear) {
      ButtonModel(
        text = "Clear selection",
        treatment = ButtonModel.Treatment.Secondary,
        size = Footer,
        onClick = StandardClick(onClear)
      )
    } else {
      null
    }
  )

data class UtxoSelectionListItem(
  val valueLabel: String,
  val outpointLabel: String,
  val isSelected: Boolean,
  val onToggle: () -> Unit,
)
