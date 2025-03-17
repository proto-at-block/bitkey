package build.wallet.ui.app.recovery

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.Progress
import build.wallet.statemachine.recovery.inprogress.waiting.AppDelayNotifyInProgressBodyModel
import build.wallet.ui.tooling.PreviewWalletTheme
import kotlin.time.Duration.Companion.seconds

@Preview
@Composable
fun AppDelayNotifyInProgressPreview() {
  PreviewWalletTheme {
    AppDelayNotifyInProgressScreen(
      model =
        AppDelayNotifyInProgressBodyModel(
          onStopRecovery = { },
          durationTitle = "18 hours",
          progress = Progress.Half,
          remainingDelayPeriod = 120.seconds,
          onExit = null
        )
    )
  }
}
