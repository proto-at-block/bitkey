package build.wallet.statemachine.settings.full.device.fingerprints.fingerprintreset

import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.recovery.inprogress.waiting.AppDelayNotifyInProgressBodyModel
import kotlinx.datetime.Instant

data class FingerprintResetDelayAndNotifyProgressBodyModel(
  val actionId: String,
  val startTime: Instant,
  val endTime: Instant,
  val completionToken: String,
  val cancellationToken: String,
) {
  fun toScreenModel(
    headline: String = "Fingerprint reset in progress...",
    delayInfoText: String = "You'll be able to add new fingerprints at the end of the 7-day security period.",
    cancelWarningText: String = "To continue using your current fingerprints, cancel the reset process.",
    cancelText: String = "Cancel reset",
    durationTitle: String,
    progress: build.wallet.Progress,
    remainingDelayPeriod: kotlin.time.Duration,
    onExit: () -> Unit,
    onStopRecovery: () -> Unit,
  ): ScreenModel =
    ScreenModel(
      body = AppDelayNotifyInProgressBodyModel(
        headline = headline,
        delayInfoText = delayInfoText,
        cancelWarningText = cancelWarningText,
        cancelText = cancelText,
        durationTitle = durationTitle,
        progress = progress,
        remainingDelayPeriod = remainingDelayPeriod,
        onExit = onExit,
        onStopRecovery = onStopRecovery,
        eventTrackerScreenInfo = EventTrackerScreenInfo(
          eventTrackerScreenId = FingerprintResetEventTrackerScreenId.RESET_FINGERPRINTS_PROGRESS
        )
      ),
      presentationStyle = ScreenPresentationStyle.Modal
    )
}
