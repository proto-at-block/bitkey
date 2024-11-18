package build.wallet.ui.app.dev.logs

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.dev.logs.LogRowModel
import build.wallet.statemachine.dev.logs.LogsBodyModel
import build.wallet.statemachine.dev.logs.LogsModel
import build.wallet.ui.tooling.PreviewWalletTheme

@Preview
@Composable
internal fun LogsScreenPreview() {
  PreviewWalletTheme {
    LogsScreen(
      model = LogsBodyModel(
        errorsOnly = false,
        analyticsEventsOnly = false,
        onErrorsOnlyValueChanged = {},
        onAnalyticsEventsOnlyValueChanged = {},
        onClear = {},
        logsModel =
          LogsModel(
            logRows =
              immutableListOf(
                LogRowModel(
                  dateTime = "05:15:06.123",
                  level = "INFO",
                  tag = "build.wallet",
                  isError = false,
                  message = "hi there"
                ),
                LogRowModel(
                  dateTime = "05:15:06.123",
                  level = "ERROR",
                  tag = "build.wallet",
                  isError = true,
                  message = "something went wrong!"
                ),
                LogRowModel(
                  dateTime = "05:15:06.123",
                  level = "INFO",
                  tag = "build.wallet",
                  isError = false,
                  message = "important message! ".repeat(30)
                )
              )
          ),
        onBack = {}
      )
    )
  }
}
