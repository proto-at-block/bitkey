package build.wallet.statemachine.account.create.full.onboard

import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.analytics.events.screen.id.CreateAccountEventTrackerScreenId
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareBodyModel
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareBodyModel.BackgroundVideo
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareBodyModel.BackgroundVideo.VideoContent.BitkeyPair
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.video.VideoStartingPosition.START

fun BuildHardwareDescriptorIntroBodyModel(
  onTapBitkey: () -> Unit,
  onBack: () -> Unit,
) = PairNewHardwareBodyModel(
  onBack = onBack,
  header = FormHeaderModel(
    headline = "Create Your Wallet",
    subline = "Tap one more time to create your wallet."
  ),
  primaryButton = ButtonModel(
    text = "Continue",
    onClick = StandardClick(onTapBitkey),
    treatment = ButtonModel.Treatment.BitkeyInteraction,
    size = ButtonModel.Size.Footer,
    leadingIcon = Icon.SmallIconBitkey
  ),
  backgroundVideo = BackgroundVideo(
    content = BitkeyPair,
    startingPosition = START
  ),
  heroImageContent = PairNewHardwareBodyModel.HeroImageContent.BuildHardwareDescriptor,
  isNavigatingBack = false,
  eventTrackerScreenInfo = EventTrackerScreenInfo(
    eventTrackerScreenId = CreateAccountEventTrackerScreenId.BUILD_HARDWARE_DESCRIPTOR_INTRO,
    eventTrackerContext = NfcEventTrackerScreenIdContext.VERIFY_KEYS_AND_BUILD_HARDWARE_DESCRIPTOR
  )
)
