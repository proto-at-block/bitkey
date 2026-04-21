package build.wallet.statemachine.account.create.full.hardware

import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.analytics.events.screen.context.PairHardwareEventTrackerScreenIdContext
import build.wallet.analytics.events.screen.id.PairHardwareEventTrackerScreenId
import build.wallet.statemachine.account.create.full.PAIRING_INSTRUCTIONS_SUFFIX
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareBodyModel.BackgroundVideo.VideoContent.BitkeyPair
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.video.VideoStartingPosition.END
import build.wallet.ui.model.video.VideoStartingPosition.START

fun StartFingerprintEnrollmentInstructionsBodyModel(
  onButtonClick: () -> Unit,
  onBack: (() -> Unit)?,
  eventTrackerScreenIdContext: PairHardwareEventTrackerScreenIdContext,
  isNavigatingBack: Boolean,
  isDesignSystemV2Enabled: Boolean,
) = PairNewHardwareBodyModel(
  onBack = onBack,
  header =
    FormHeaderModel(
      headline = "Pair your Bitkey device",
      subline =
        "Activate your phone’s NFC reader with the button below. Then hold your Bitkey to the back of your phone." +
          PAIRING_INSTRUCTIONS_SUFFIX
    ),
  primaryButton =
    ButtonModel(
      text = "Pair Bitkey Device",
      requiresBitkeyInteraction = isDesignSystemV2Enabled,
      size = ButtonModel.Size.Footer,
      treatment = ButtonModel.Treatment.Translucent,
      leadingIcon = Icon.SmallIconBitkey,
      onClick = onButtonClick
    ),
  backgroundVideo = PairNewHardwareBodyModel.BackgroundVideo(
    content = BitkeyPair,
    startingPosition = if (isNavigatingBack) END else START
  ),
  isNavigatingBack = isNavigatingBack,
  eventTrackerScreenInfo =
    EventTrackerScreenInfo(
      eventTrackerScreenId = PairHardwareEventTrackerScreenId.HW_PAIR_INSTRUCTIONS,
      eventTrackerContext = eventTrackerScreenIdContext
    )
)
