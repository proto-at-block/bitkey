package build.wallet.statemachine.account.create.full.hardware

import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.analytics.events.screen.context.EventTrackerScreenIdContext
import build.wallet.analytics.events.screen.id.PairHardwareEventTrackerScreenId.FINGERPRINT_ENROLLMENT_ERROR_SHEET
import build.wallet.analytics.events.screen.id.PairHardwareEventTrackerScreenId.HW_SAVE_FINGERPRINT_INSTRUCTIONS
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareBodyModel.BackgroundVideo.VideoContent.BitkeyFingerprint
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorFormBodyModel
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.ScreenColorMode
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.RenderContext.Sheet
import build.wallet.ui.model.Click
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Footer
import build.wallet.ui.model.video.VideoStartingPosition.END
import build.wallet.ui.model.video.VideoStartingPosition.START

fun HardwareFingerprintEnrollmentScreenModel(
  onSaveFingerprint: () -> Unit,
  onBack: () -> Unit,
  showingIncompleteEnrollmentError: Boolean,
  incompleteEnrollmentErrorOnPrimaryButtonClick: () -> Unit,
  onErrorOverlayClosed: () -> Unit,
  eventTrackerScreenIdContext: EventTrackerScreenIdContext,
  presentationStyle: ScreenPresentationStyle,
  isNavigatingBack: Boolean,
) = ScreenModel(
  body =
    PairNewHardwareBodyModel(
      onBack = onBack,
      header =
        FormHeaderModel(
          headline = "Set up your fingerprint",
          subline =
            "Place your finger on the sensor until you see a blue light." +
              " Repeat this several times until the device has a solid green light." +
              " Once done, press the button below to save your fingerprint."
        ),
      primaryButton =
        ButtonModel(
          text = "Save fingerprint",
          treatment = ButtonModel.Treatment.Translucent,
          leadingIcon = Icon.SmallIconBitkey,
          onClick = Click.standardClick { onSaveFingerprint() },
          size = Footer,
          testTag = "save-fingerprint"
        ),
      backgroundVideo = PairNewHardwareBodyModel.BackgroundVideo(
        content = BitkeyFingerprint,
        startingPosition = if (isNavigatingBack) END else START
      ),
      keepScreenOn = true,
      eventTrackerScreenInfo =
        EventTrackerScreenInfo(
          eventTrackerScreenId = HW_SAVE_FINGERPRINT_INSTRUCTIONS,
          eventTrackerScreenIdContext = eventTrackerScreenIdContext
        )
    ),
  presentationStyle = presentationStyle,
  bottomSheetModel =
    if (showingIncompleteEnrollmentError) {
      SheetModel(
        onClosed = onErrorOverlayClosed,
        body =
          ErrorFormBodyModel(
            title = "Incomplete Fingerprint Scan",
            subline = "Please continue scanning your fingerprint and try saving again.",
            primaryButton =
              ButtonDataModel(
                text = "Got it",
                onClick = incompleteEnrollmentErrorOnPrimaryButtonClick
              ),
            renderContext = Sheet,
            eventTrackerScreenId = FINGERPRINT_ENROLLMENT_ERROR_SHEET,
            eventTrackerScreenIdContext = eventTrackerScreenIdContext
          )
      )
    } else {
      null
    },
  colorMode = ScreenColorMode.Dark
)
