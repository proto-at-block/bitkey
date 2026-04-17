package build.wallet.statemachine.fwup

import build.wallet.analytics.events.screen.id.FwupEventTrackerScreenId
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormDesignSystemV2Model
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.tokens.LabelType

/**
 * Screen shown between MCU updates in a sequence, prompting the user to start the next component.
 *
 * @param completedIndex 1-based index of the completed MCU (e.g., 1 for first)
 * @param totalMcus Total number of MCUs to update
 * @param onContinue Called when user taps to continue with next component
 * @param onBack Called when user cancels the update
 */
data class FwupNextComponentReadyModel(
  val completedIndex: Int,
  val totalMcus: Int,
  override val onBack: () -> Unit,
  val onContinue: () -> Unit,
) : FormBodyModel(
    id = FwupEventTrackerScreenId.FWUP_NEXT_COMPONENT_READY,
    onBack = onBack,
    toolbar = null,
    header = null,
    mainContentList = immutableListOf(
      FormMainContentModel.Showcase(
        content = FormMainContentModel.Showcase.Content.IconContent(
          icon = Icon.BitkeyDevice3D
        ),
        title = "Update $completedIndex of $totalMcus complete",
        body = LabelModel.StringModel(
          "Press the button below and hold your unlocked device to the back of your phone to continue the update."
        )
      )
    ),
    designSystemV2Model = FormDesignSystemV2Model(
      header = null,
      useLegacyHeaderFallback = false,
      mainContentList = immutableListOf(
        FormMainContentModel.HeaderBlock(
          header = FormHeaderModel(
            headline = "Update $completedIndex of $totalMcus complete",
            subline = "Press the button below and hold your unlocked device to the back of your phone to continue the update.",
            alignment = FormHeaderModel.Alignment.CENTER,
            headlineLabelType = LabelType.Body2Mono
          )
        )
      ),
      useDesignSystemV2ScreenLayout = true,
      scrollable = false,
      mainContentVerticalAlignment = FormDesignSystemV2Model.MainContentVerticalAlignment.CENTER
    ),
    primaryButton = ButtonModel(
      text = "Continue update",
      requiresBitkeyInteraction = true,
      treatment = ButtonModel.Treatment.BitkeyInteraction,
      size = ButtonModel.Size.Footer,
      onClick = onContinue
    ),
    secondaryButton = ButtonModel(
      text = "Cancel",
      requiresBitkeyInteraction = false,
      treatment = ButtonModel.Treatment.Secondary,
      size = ButtonModel.Size.Footer,
      onClick = onBack
    )
  )
