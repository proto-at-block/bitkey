package build.wallet.statemachine.account.create.full.hardware

import build.wallet.analytics.events.EventTrackerContext
import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.analytics.events.screen.id.PairHardwareEventTrackerScreenId.FINGERPRINT_ENROLLMENT_ERROR_SHEET
import build.wallet.analytics.events.screen.id.PairHardwareEventTrackerScreenId.HW_SAVE_FINGERPRINT_INSTRUCTIONS
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareBodyModel.BackgroundVideo.VideoContent.BitkeyFingerprint
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorFormBodyModel
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.RenderContext.Sheet
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Footer
import build.wallet.ui.model.video.VideoStartingPosition.END
import build.wallet.ui.model.video.VideoStartingPosition.START
import build.wallet.ui.theme.Theme
import build.wallet.ui.theme.ThemePreference

fun HardwareFingerprintEnrollmentScreenModel(
  onSaveFingerprint: () -> Unit,
  onBack: () -> Unit,
  showingIncompleteEnrollmentError: Boolean,
  incompleteEnrollmentErrorOnPrimaryButtonClick: () -> Unit,
  onErrorOverlayClosed: () -> Unit,
  eventTrackerContext: EventTrackerContext,
  presentationStyle: ScreenPresentationStyle,
  isNavigatingBack: Boolean,
  headline: String,
  instructions: String,
) = ScreenModel(
  body =
    PairNewHardwareBodyModel(
      onBack = onBack,
      header =
        FormHeaderModel(
          headline = headline,
          subline = instructions
        ),
      primaryButton =
        ButtonModel(
          text = "Save fingerprint",
          treatment = ButtonModel.Treatment.Translucent,
          leadingIcon = Icon.SmallIconBitkey,
          onClick = StandardClick(onSaveFingerprint),
          size = Footer,
          testTag = "save-fingerprint"
        ),
      backgroundVideo = PairNewHardwareBodyModel.BackgroundVideo(
        content = BitkeyFingerprint,
        startingPosition = if (isNavigatingBack) END else START
      ),
      isNavigatingBack = isNavigatingBack,
      keepScreenOn = true,
      eventTrackerScreenInfo =
        EventTrackerScreenInfo(
          eventTrackerScreenId = HW_SAVE_FINGERPRINT_INSTRUCTIONS,
          eventTrackerContext = eventTrackerContext
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
            eventTrackerContext = eventTrackerContext
          )
      )
    } else {
      null
    },
  themePreference = ThemePreference.Manual(Theme.DARK)
)
