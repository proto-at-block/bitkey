package build.wallet.statemachine.nfc

import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.analytics.events.screen.id.NfcEventTrackerScreenId
import build.wallet.compose.collections.buildImmutableList
import build.wallet.nfc.platform.EmulatedPromptOption
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.form.RenderContext
import build.wallet.ui.model.SheetClosingClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Footer
import build.wallet.ui.model.button.ButtonModel.Treatment.Primary
import build.wallet.ui.model.button.ButtonModel.Treatment.SecondaryDestructive
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.CloseAccessory
import build.wallet.ui.model.toolbar.ToolbarModel

/**
 * Form body model for displaying an emulated device confirmation prompt.
 * Used by NFC state machines to simulate device confirmation prompts in fake/debug mode.
 */
internal data class PromptSelectionFormBodyModel(
  val details: List<EmulatedPromptOption.Detail> = emptyList(),
  val onApprove: () -> Unit,
  val onDeny: () -> Unit,
  override val onBack: () -> Unit,
  override val eventTrackerContext: NfcEventTrackerScreenIdContext,
) : FormBodyModel(
    id = NfcEventTrackerScreenId.NFC_INITIATE,
    onBack = onBack,
    toolbar = ToolbarModel(leadingAccessory = CloseAccessory(onBack)),
    header = FormHeaderModel(
      headline = "Select Option",
      subline = "Choose the response you would like to simulate."
    ),
    mainContentList = buildImmutableList {
      if (details.isNotEmpty()) {
        add(
          FormMainContentModel.ListGroup(
            listGroupModel = ListGroupModel(
              items = buildImmutableList {
                details.forEach { detail ->
                  add(
                    ListItemModel(
                      title = detail.label,
                      secondaryText = detail.value,
                      onClick = {}
                    )
                  )
                }
              },
              style = ListGroupStyle.CARD_GROUP
            )
          )
        )
      }
    },
    primaryButton = ButtonModel(
      text = "Approve",
      treatment = Primary,
      size = Footer,
      onClick = SheetClosingClick { onApprove() }
    ),
    secondaryButton = ButtonModel(
      text = "Deny",
      treatment = SecondaryDestructive,
      size = Footer,
      onClick = SheetClosingClick { onDeny() }
    ),
    eventTrackerContext = eventTrackerContext,
    renderContext = RenderContext.Sheet
  )
