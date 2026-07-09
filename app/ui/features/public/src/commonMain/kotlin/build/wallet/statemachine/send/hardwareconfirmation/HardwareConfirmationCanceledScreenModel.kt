package build.wallet.statemachine.send.hardwareconfirmation

import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormHeaderModel.Alignment.CENTER
import build.wallet.statemachine.core.form.FormHeaderModel.SublineTreatment.SMALL
import build.wallet.statemachine.core.form.FormMainContentVerticalAlignment
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.form.FormScreenLayoutModel
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.tokens.LabelType

data class HardwareConfirmationCanceledScreenModel(
  override val onBack: () -> Unit,
  val content: HardwareConfirmationContent = HardwareConfirmationContent.SignTransaction,
) : FormBodyModel(
    id = content.canceledScreenId,
    onBack = onBack,
    toolbar = null,
    header = null,
    formScreenLayout = FormScreenLayoutModel.LargeTitle(
      scrollable = false,
      mainContentVerticalAlignment = FormMainContentVerticalAlignment.CENTER
    ),
    mainContentList = immutableListOf(
      FormMainContentModel.HeaderBlock(
        header = FormHeaderModel(
          headline = content.canceledTitle,
          subline = content.canceledBody,
          alignment = CENTER,
          sublineTreatment = SMALL,
          headlineLabelType = LabelType.Body2Mono
        )
      )
    ),
    primaryButton = ButtonModel(
      text = "Done",
      leadingIcon = null,
      requiresBitkeyInteraction = false,
      treatment = ButtonModel.Treatment.BitkeyInteraction,
      size = ButtonModel.Size.Footer,
      onClick = onBack
    )
  )
