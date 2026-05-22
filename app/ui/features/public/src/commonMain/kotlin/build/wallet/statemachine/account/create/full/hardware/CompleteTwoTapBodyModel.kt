package build.wallet.statemachine.account.create.full.hardware

import build.wallet.analytics.events.EventTrackerContext
import build.wallet.analytics.events.screen.id.PairHardwareEventTrackerScreenId
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

/**
 * W3 onboarding: "Finished on your device?" screen.
 * Shown after the first NFC tap while user completes fingerprint enrollment on hardware.
 */
data class CompleteTwoTapBodyModel(
  override val onBack: () -> Unit,
  val onContinue: () -> Unit,
  val onHelpClick: () -> Unit,
  override val eventTrackerContext: EventTrackerContext,
  val isHardwareFake: Boolean = false,
) : FormBodyModel(
    id = PairHardwareEventTrackerScreenId.HW_COMPLETE_TWO_TAP,
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
          testTag = "back",
          onClick = StandardClick(onBack)
        )
      ),
      trailingAccessory = ToolbarAccessoryModel.IconAccessory(
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
          testTag = "help",
          onClick = StandardClick(onHelpClick)
        )
      )
    ),
    header = FormHeaderModel(
      headline = "Finished on your device?",
      subline = "Finish the steps on your device before continuing.",
      headlineLabelType = LabelType.Display2
    ),
    primaryButton = ButtonModel(
      text = "Yes, continue",
      onClick = StandardClick(onContinue),
      treatment = ButtonModel.Treatment.BitkeyInteraction,
      size = ButtonModel.Size.Footer,
      leadingIcon = Icon.SmallIconBitkey
    ),
    eventTrackerContext = eventTrackerContext,
    designSystemV2Model = FormDesignSystemV2Model(
      toolbar = ToolbarModel(
        leadingAccessory = BackAccessory(onBack),
        trailingAccessory = QuestionAccessory(onHelpClick)
      ),
      header = null,
      useLegacyHeaderFallback = false,
      mainContentList = immutableListOf(
        FormMainContentModel.HeaderBlock(
          header = FormHeaderModel(
            headline = "Review on your Bitkey",
            subline = "Follow the instructions on the device, then continue.",
            iconModel = null,
            alignment = CENTER,
            sublineTreatment = SMALL,
            headlineLabelType = LabelType.Body2MonoCaps,
            customContent = FormHeaderModel.CustomContent.ScanAnimation
          )
        )
      ),
      primaryButton = ButtonModel(
        text = "Continue",
        onClick = StandardClick(onContinue),
        treatment = ButtonModel.Treatment.BitkeyInteraction,
        size = ButtonModel.Size.Footer,
        leadingIcon = Icon.SmallIconBitkey
      ),
      footerRevealDelayMillis = formDsV2WaitingRevealDelayMillis(isHardwareFake),
      useDesignSystemV2ScreenLayout = true,
      scrollable = false,
      mainContentVerticalAlignment = FormDesignSystemV2Model.MainContentVerticalAlignment.CENTER
    )
  )
