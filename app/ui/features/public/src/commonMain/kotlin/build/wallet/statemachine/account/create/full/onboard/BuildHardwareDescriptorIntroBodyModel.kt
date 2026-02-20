package build.wallet.statemachine.account.create.full.onboard

import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.analytics.events.screen.id.CreateAccountEventTrackerScreenId
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareBodyModel
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.video.VideoStartingPosition.END

/**
 * Body model for the Build Hardware Descriptor intro screen.
 *
 * Shows information about the hardware descriptor building process
 * and prompts the user to tap their Bitkey device.
 */
fun BuildHardwareDescriptorIntroBodyModel(
  onTapBitkey: (() -> Unit)?,
  onBack: (() -> Unit)?,
) = PairNewHardwareBodyModel(
  onBack = onBack,
  header =
    FormHeaderModel(
      headline = "Create your Bitkey wallet",
      subline = "Complete the pairing process to create your wallet and start receiving funds."
    ),
  primaryButton =
    ButtonModel(
      text = "Approve with Bitkey device",
      onClick = StandardClick { onTapBitkey?.invoke() },
      treatment = ButtonModel.Treatment.White,
      leadingIcon = Icon.SmallIconBitkey,
      size = ButtonModel.Size.Footer
    ),
  backgroundVideo = PairNewHardwareBodyModel.BackgroundVideo(
    content = PairNewHardwareBodyModel.BackgroundVideo.VideoContent.BitkeyPair,
    startingPosition = END
  ),
  isNavigatingBack = false,
  eventTrackerScreenInfo =
    EventTrackerScreenInfo(
      eventTrackerScreenId = CreateAccountEventTrackerScreenId.BUILD_HARDWARE_DESCRIPTOR_INTRO,
      eventTrackerContext = NfcEventTrackerScreenIdContext.VERIFY_KEYS_AND_BUILD_HARDWARE_DESCRIPTOR
    )
)
