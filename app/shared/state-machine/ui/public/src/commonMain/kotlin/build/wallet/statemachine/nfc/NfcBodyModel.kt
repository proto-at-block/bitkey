package build.wallet.statemachine.nfc

import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.ScreenColorMode.Dark
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle.FullScreen

data class NfcBodyModel(
  val text: String,
  val status: Status,
  override val eventTrackerScreenInfo: EventTrackerScreenInfo?,
) : BodyModel() {
  /**
   * Convenience method to wrap NFC screen model into full screen model.
   */
  fun asFullScreen() =
    ScreenModel(
      body = this,
      presentationStyle = FullScreen,
      colorMode = Dark
    )

  sealed class Status {
    data class Searching(
      val onCancel: () -> Unit,
    ) : Status()

    data class Connected(
      val onCancel: () -> Unit,
      /** Whether we want to show an indeterminate progress spinner (on Android) during the NFC operation */
      val showProgressSpinner: Boolean = false,
    ) : Status()

    data object Success : Status()
  }
}
