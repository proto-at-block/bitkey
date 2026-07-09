package build.wallet.statemachine.send.hardwareconfirmation

import build.wallet.compose.collections.buildImmutableList
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormHeaderModel.Alignment.CENTER
import build.wallet.statemachine.core.form.FormHeaderModel.SublineTreatment.SMALL
import build.wallet.statemachine.core.form.FormMainContentVerticalAlignment
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.form.FormScreenLayoutModel
import build.wallet.statemachine.core.form.formWaitingRevealDelayMillis
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.QuestionAccessory
import build.wallet.ui.model.toolbar.ToolbarModel
import build.wallet.ui.tokens.LabelType

data class HardwareConfirmationScreenModel(
  override val onBack: () -> Unit,
  val onCancel: () -> Unit = onBack,
  val onConfirm: () -> Unit,
  val onHelpClick: (() -> Unit)? = null,
  val content: HardwareConfirmationContent = HardwareConfirmationContent.SignTransaction,
  val isHardwareFake: Boolean = false,
) : FormBodyModel(
    id = content.screenId,
    onBack = onBack,
    toolbar = ToolbarModel(
      leadingAccessory = BackAccessory(onBack),
      trailingAccessory =
        onHelpClick?.let { onHelp ->
          QuestionAccessory(onHelp)
        }
    ),
    header = null,
    formScreenLayout = FormScreenLayoutModel.LargeTitle(
      scrollable = false,
      mainContentVerticalAlignment = FormMainContentVerticalAlignment.CENTER
    ),
    mainContentList = immutableListOf(
      FormMainContentModel.HeaderBlock(
        header = FormHeaderModel(
          headline = content.title,
          subline = content.body,
          iconModel = null,
          alignment = CENTER,
          sublineTreatment = SMALL,
          headlineLabelType = LabelType.Body2MonoCaps,
          customContent = FormHeaderModel.CustomContent.ScanAnimation
        )
      )
    ),
    primaryButton = ButtonModel(
      text = content.confirmButtonText,
      treatment = ButtonModel.Treatment.BitkeyInteraction,
      size = ButtonModel.Size.Footer,
      onClick = StandardClick(onConfirm),
      leadingIcon = Icon.Bitkey
    ),
    secondaryButton = ButtonModel(
      text = content.cancelButtonText,
      treatment = ButtonModel.Treatment.SecondaryDestructive,
      size = ButtonModel.Size.Footer,
      onClick = StandardClick(onCancel)
    ),
    footerRevealDelayMillis = formWaitingRevealDelayMillis(isHardwareFake),
    preFooterContentList = buildImmutableList {
      content.recipientAddress?.let { address ->
        add(
          FormMainContentModel.CollapsibleAddress(
            address = address.address,
            label = "DESTINATION ADDRESS"
          )
        )
      }
    }
  )
