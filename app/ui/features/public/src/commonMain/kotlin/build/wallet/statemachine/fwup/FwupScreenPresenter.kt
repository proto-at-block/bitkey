package build.wallet.statemachine.fwup

import androidx.compose.runtime.Composable
import bitkey.ui.framework.Navigator
import bitkey.ui.framework.Screen
import bitkey.ui.framework.ScreenPresenter
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.fwup.FirmwareData
import build.wallet.statemachine.core.*

data class FwupScreen(
  val firmwareUpdateData: FirmwareData.FirmwareUpdateState,
  val onExit: (() -> Unit)? = null,
) : Screen

class FwupSegment : AppSegment {
  override val id: String = "FirmwareUpdate"
}

@BitkeyInject(ActivityScope::class)
class FwupScreenPresenter(
  private val fwupNfcUiStateMachine: FwupNfcUiStateMachine,
) : ScreenPresenter<FwupScreen> {
  @Composable
  override fun model(
    navigator: Navigator,
    screen: FwupScreen,
  ): ScreenModel {
    val exit = screen.onExit ?: { navigator.exit() }
    return when (screen.firmwareUpdateData) {
      is FirmwareData.FirmwareUpdateState.PendingUpdate ->
        fwupNfcUiStateMachine.model(
          props =
            FwupNfcUiProps(
              onDone = exit
            )
        )
      else -> ScreenModel(
        body = ErrorFormBodyModel(
          title = "No update available",
          subline = "Your device is up to date.",
          primaryButton = ButtonDataModel("Got it", isLoading = false, onClick = exit),
          errorData = ErrorData(
            segment = FwupSegment(),
            actionDescription = "Starting firmware update",
            cause = Throwable("Expected PendingUpdate state, but got ${screen.firmwareUpdateData}")
          ),
          eventTrackerScreenId = null
        ),
        presentationStyle = ScreenPresentationStyle.ModalFullScreen
      )
    }
  }
}
