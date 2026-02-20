package build.wallet.statemachine.account.create.full.hardware

import build.wallet.analytics.events.EventTrackerContext
import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.analytics.events.screen.id.PairHardwareEventTrackerScreenId
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareBodyModel.BackgroundVideo.VideoContent.BitkeyFingerprint
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.video.VideoStartingPosition.END
import build.wallet.ui.model.video.VideoStartingPosition.START

fun ActivationInstructionsV2BodyModel(
  onContinue: (() -> Unit)?,
  onNoScreenClick: () -> Unit,
  onBack: () -> Unit,
  isNavigatingBack: Boolean,
  eventTrackerContext: EventTrackerContext,
) = PairNewHardwareBodyModel(
  onBack = onBack,
  header = FormHeaderModel(
    headline = "Let's get set up",
    subline = "Your device needs to be awake, then a fingerprint is needed to set up your " +
      "Bitkey account. You'll need to do a hardware round trip."
  ),
  primaryButton = ButtonModel(
    text = "Tap to get started",
    onClick = StandardClick { onContinue?.invoke() },
    treatment = ButtonModel.Treatment.Primary,
    size = ButtonModel.Size.Footer,
    leadingIcon = Icon.SmallIconBitkey,
    isLoading = onContinue == null
  ),
  secondaryButton = ButtonModel(
    text = "My Bitkey doesn't have a screen",
    onClick = StandardClick(onNoScreenClick),
    treatment = ButtonModel.Treatment.Secondary,
    size = ButtonModel.Size.Footer
  ),
  backgroundVideo = PairNewHardwareBodyModel.BackgroundVideo(
    content = BitkeyFingerprint,
    startingPosition = if (isNavigatingBack) END else START
  ),
  isNavigatingBack = isNavigatingBack,
  eventTrackerScreenInfo = EventTrackerScreenInfo(
    eventTrackerScreenId = PairHardwareEventTrackerScreenId.HW_ACTIVATION_INSTRUCTIONS_V2,
    eventTrackerContext = eventTrackerContext
  )
)
