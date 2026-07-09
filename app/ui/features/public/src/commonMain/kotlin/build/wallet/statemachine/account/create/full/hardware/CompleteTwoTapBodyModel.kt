package build.wallet.statemachine.account.create.full.hardware

import build.wallet.analytics.events.EventTrackerContext
import build.wallet.analytics.events.screen.id.PairHardwareEventTrackerScreenId
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
      leadingAccessory = BackAccessory(onBack),
      trailingAccessory = QuestionAccessory(onHelpClick)
    ),
    header = null,
    formScreenLayout = FormScreenLayoutModel.LargeTitle(
      scrollable = false,
      mainContentVerticalAlignment = FormMainContentVerticalAlignment.CENTER
    ),
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
      leadingIcon = Icon.Bitkey
    ),
    footerRevealDelayMillis = formWaitingRevealDelayMillis(isHardwareFake),
    eventTrackerContext = eventTrackerContext
  )
