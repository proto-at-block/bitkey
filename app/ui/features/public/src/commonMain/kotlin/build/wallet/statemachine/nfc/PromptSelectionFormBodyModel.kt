package build.wallet.statemachine.nfc

import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.analytics.events.screen.id.NfcEventTrackerScreenId
import build.wallet.compose.collections.buildImmutableList
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarModel

/**
 * Form body model for displaying a list of selectable prompt options.
 * Used by NFC state machines to simulate device confirmation prompts in fake/debug mode.
 */
internal data class PromptSelectionFormBodyModel(
  val options: List<String>,
  val onOptionSelected: (Int) -> Unit,
  override val onBack: () -> Unit,
  override val eventTrackerContext: NfcEventTrackerScreenIdContext,
) : FormBodyModel(
    id = NfcEventTrackerScreenId.NFC_INITIATE,
    onBack = onBack,
    toolbar = ToolbarModel(leadingAccessory = BackAccessory(onBack)),
    header = FormHeaderModel(
      headline = "Select Option",
      subline = "Choose the response you would like to simulate."
    ),
    mainContentList = buildImmutableList {
      add(
        FormMainContentModel.ListGroup(
          listGroupModel = ListGroupModel(
            items = buildImmutableList {
              options.forEachIndexed { index, optionName ->
                add(ListItemModel(title = optionName, onClick = { onOptionSelected(index) }))
              }
            },
            style = ListGroupStyle.CARD_ITEM
          )
        )
      )
    },
    primaryButton = null,
    eventTrackerContext = eventTrackerContext
  )
