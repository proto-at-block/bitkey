package build.wallet.statemachine.account.create.full.hardware

import build.wallet.analytics.events.EventTrackerContext
import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.analytics.events.screen.id.PairHardwareEventTrackerScreenId
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareBodyModel.BackgroundVideo.VideoContent.BitkeyActivate
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.video.VideoStartingPosition.END
import build.wallet.ui.model.video.VideoStartingPosition.START

fun ActivationInstructionsBodyModel(
  onContinue: (() -> Unit)?,
  onBack: () -> Unit,
  isNavigatingBack: Boolean,
  eventTrackerContext: EventTrackerContext,
) = PairNewHardwareBodyModel(
  onBack = onBack,
  header =
    FormHeaderModel(
      headline = "Wake your Bitkey device",
      subline =
        "Touch the fingerprint reader until you see a white light." +
          " If your device doesn’t turn on, charge your device and try again."
    ),
  primaryButton =
    ButtonModel(
      text = "Continue",
      onClick = StandardClick { onContinue?.invoke() },
      treatment = ButtonModel.Treatment.White,
      size = ButtonModel.Size.Footer,
      isLoading = onContinue == null
    ),
  backgroundVideo = PairNewHardwareBodyModel.BackgroundVideo(
    content = BitkeyActivate,
    startingPosition = if (isNavigatingBack) END else START
  ),
  isNavigatingBack = isNavigatingBack,
  eventTrackerScreenInfo =
    EventTrackerScreenInfo(
      eventTrackerScreenId = PairHardwareEventTrackerScreenId.HW_ACTIVATION_INSTRUCTIONS,
      eventTrackerContext = eventTrackerContext
    )
)
