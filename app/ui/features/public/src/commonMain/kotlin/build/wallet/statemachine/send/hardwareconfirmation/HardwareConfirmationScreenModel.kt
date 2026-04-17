package build.wallet.statemachine.send.hardwareconfirmation

import build.wallet.compose.collections.buildImmutableList
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormDesignSystemV2Model
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormHeaderModel.Alignment.CENTER
import build.wallet.statemachine.core.form.FormHeaderModel.SublineTreatment.SMALL
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.form.formDsV2WaitingRevealDelayMillis
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.icon.IconBackgroundType
import build.wallet.ui.model.icon.IconButtonModel
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
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
      leadingAccessory = ToolbarAccessoryModel.IconAccessory(
        model = IconButtonModel(
          iconModel = IconModel(
            icon = Icon.SmallIconArrowLeft,
            iconSize = IconSize.Accessory,
            iconBackgroundType =
              IconBackgroundType.Circle(
                circleSize = IconSize.Regular,
                color = IconBackgroundType.Circle.CircleColor.Secondary
              ),
            iconTint = IconTint.Foreground
          ),
          onClick = StandardClick(onBack)
        )
      ),
      trailingAccessory =
        onHelpClick?.let { onHelp ->
          ToolbarAccessoryModel.IconAccessory(
            model = IconButtonModel(
              iconModel = IconModel(
                icon = Icon.SmallIconQuestion,
                iconSize = IconSize.Accessory,
                iconBackgroundType =
                  IconBackgroundType.Circle(
                    circleSize = IconSize.Regular,
                    color = IconBackgroundType.Circle.CircleColor.Secondary
                  ),
                iconTint = IconTint.Foreground
              ),
              onClick = StandardClick(onHelp)
            )
          )
        }
    ),
    header = FormHeaderModel(
      headline = content.title,
      subline = content.body,
      headlineLabelType = LabelType.Display2
    ),
    mainContentList = immutableListOf(
      FormMainContentModel.Spacer(),
      FormMainContentModel.Showcase(
        content = FormMainContentModel.Showcase.Content.IconContent(
          icon = Icon.NfcTwoTap,
          widthDp = 125,
          heightDp = 172
        )
      ),
      FormMainContentModel.Spacer()
    ),
    primaryButton = ButtonModel(
      text = content.confirmButtonText,
      treatment = ButtonModel.Treatment.BitkeyInteraction,
      size = ButtonModel.Size.Footer,
      onClick = StandardClick(onConfirm),
      leadingIcon = Icon.SmallIconBitkey
    ),
    secondaryButton = ButtonModel(
      text = content.cancelButtonText,
      treatment = ButtonModel.Treatment.SecondaryDestructive,
      size = ButtonModel.Size.Footer,
      onClick = StandardClick(onCancel)
    ),
    designSystemV2Model = FormDesignSystemV2Model(
      toolbar = ToolbarModel(
        leadingAccessory = BackAccessory(onBack),
        trailingAccessory =
          onHelpClick?.let { onHelp ->
            QuestionAccessory(onHelp)
          }
      ),
      header = null,
      useLegacyHeaderFallback = false,
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
        leadingIcon = Icon.SmallIconBitkey
      ),
      secondaryButton = ButtonModel(
        text = content.cancelButtonText,
        treatment = ButtonModel.Treatment.SecondaryDestructive,
        size = ButtonModel.Size.Footer,
        onClick = StandardClick(onCancel)
      ),
      footerRevealDelayMillis = formDsV2WaitingRevealDelayMillis(isHardwareFake),
      useDesignSystemV2ScreenLayout = true,
      scrollable = false,
      mainContentVerticalAlignment = FormDesignSystemV2Model.MainContentVerticalAlignment.CENTER,
      preFooterMainContentList = buildImmutableList {
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
  )
