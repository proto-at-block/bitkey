package build.wallet.ui.app.recovery

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.Progress
import build.wallet.statemachine.recovery.inprogress.waiting.AppDelayNotifyInProgressBodyModel
import build.wallet.ui.tooling.PreviewWalletTheme
import kotlin.time.Duration.Companion.hours

@Preview
@Composable
fun AppDelayNotifyInProgressPreview() {
  PreviewWalletTheme {
    AppDelayNotifyInProgressScreen(
      model =
        AppDelayNotifyInProgressBodyModel(
          onStopRecovery = { },
          progress = Progress.Half,
          remainingDelayPeriod = 18.hours,
          onExit = null
        )
    )
  }
}
