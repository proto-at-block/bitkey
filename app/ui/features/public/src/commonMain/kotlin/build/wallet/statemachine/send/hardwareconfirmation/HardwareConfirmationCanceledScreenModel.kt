package build.wallet.statemachine.send.hardwareconfirmation

import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormDesignSystemV2Model
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormHeaderModel.Alignment.CENTER
import build.wallet.statemachine.core.form.FormHeaderModel.SublineTreatment.SMALL
import build.wallet.statemachine.core.form.FormMainContentModel
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
    mainContentList = immutableListOf(
      FormMainContentModel.Showcase(
        content = FormMainContentModel.Showcase.Content.IconContent(
          // TODO Replace with new icon
          icon = Icon.BitkeyDevice3D
        ),
        title = content.canceledTitle,
        body = LabelModel.StringModel(content.canceledBody)
      )
    ),
    primaryButton = ButtonModel(
      text = "Done",
      leadingIcon = null,
      requiresBitkeyInteraction = false,
      treatment = ButtonModel.Treatment.BitkeyInteraction,
      size = ButtonModel.Size.Footer,
      onClick = onBack
    ),
    designSystemV2Model = FormDesignSystemV2Model(
      header = null,
      useLegacyHeaderFallback = false,
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
      useDesignSystemV2ScreenLayout = true,
      scrollable = false,
      mainContentVerticalAlignment = FormDesignSystemV2Model.MainContentVerticalAlignment.CENTER
    )
  )
