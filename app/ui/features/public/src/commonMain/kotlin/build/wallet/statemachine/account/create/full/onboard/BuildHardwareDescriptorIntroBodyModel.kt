package build.wallet.statemachine.account.create.full.onboard

import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.analytics.events.screen.id.CreateAccountEventTrackerScreenId
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.icon.IconBackgroundType
import build.wallet.ui.model.icon.IconButtonModel
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel
import build.wallet.ui.tokens.LabelType

/**
 * Body model for the Build Hardware Descriptor intro screen.
 *
 * Shows information about the hardware descriptor building process
 * and prompts the user to tap their Bitkey device.
 */
data class BuildHardwareDescriptorIntroBodyModel(
  val onTapBitkey: () -> Unit,
  override val onBack: () -> Unit,
) : FormBodyModel(
    id = CreateAccountEventTrackerScreenId.BUILD_HARDWARE_DESCRIPTOR_INTRO,
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
                color = IconBackgroundType.Circle.CircleColor.TranslucentBlack
              ),
            iconTint = IconTint.Foreground
          ),
          testTag = "back",
          onClick = StandardClick(onBack)
        )
      )
      // Bring back the Help here!
    ),
    header = FormHeaderModel(
      headline = "Create Your Wallet",
      subline = "Tap one more time to create your wallet.",
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
      text = "Continue",
      onClick = StandardClick(onTapBitkey),
      treatment = ButtonModel.Treatment.BitkeyInteraction,
      size = ButtonModel.Size.Footer,
      leadingIcon = Icon.SmallIconBitkey
    ),
    eventTrackerContext = NfcEventTrackerScreenIdContext.VERIFY_KEYS_AND_BUILD_HARDWARE_DESCRIPTOR
  )
