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
  onBack: (() -> Unit)?,
  onHelpClick: () -> Unit,
  isNavigatingBack: Boolean,
  eventTrackerContext: EventTrackerContext,
) = PairNewHardwareBodyModel(
  onBack = onBack,
  header = FormHeaderModel(
    headline = "Set up your Bitkey",
    subline = "Tap the fingerprint sensor to wake your device." +
      "\nScan your Bitkey with your phone to get started."
  ),
  primaryButton = ButtonModel(
    text = "Let's go",
    treatment = ButtonModel.Treatment.BitkeyInteraction,
    size = ButtonModel.Size.Footer,
    leadingIcon = Icon.Bitkey,
    isLoading = onContinue == null,
    onClick = StandardClick { onContinue?.invoke() }
  ),
  toolbarTrailingIcon = Icon.Question,
  onToolbarTrailingClick = onHelpClick,
  toolbarTrailingTestTag = "help",
  backgroundVideo = PairNewHardwareBodyModel.BackgroundVideo(
    content = BitkeyFingerprint,
    startingPosition = if (isNavigatingBack) END else START
  ),
  heroImageContent = PairNewHardwareBodyModel.HeroImageContent.FingerprintSetup,
  isNavigatingBack = isNavigatingBack,
  eventTrackerScreenInfo = EventTrackerScreenInfo(
    eventTrackerScreenId = PairHardwareEventTrackerScreenId.HW_ACTIVATION_INSTRUCTIONS_V2,
    eventTrackerContext = eventTrackerContext
  )
)
