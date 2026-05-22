package build.wallet.ui.app.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.analytics.events.screen.context.PairHardwareEventTrackerScreenIdContext
import build.wallet.statemachine.account.create.full.hardware.CompleteTwoTapBodyModel
import build.wallet.ui.app.core.form.FormScreen
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tooling.PreviewWalletTheme

@Preview
@Composable
fun CompleteTwoTapScreenPreview() {
  PreviewWalletTheme {
    FormScreen(
      model = CompleteTwoTapBodyModel(
        onBack = {},
        onContinue = {},
        onHelpClick = {},
        eventTrackerContext = PairHardwareEventTrackerScreenIdContext.ACCOUNT_CREATION
      )
    )
  }
}

@Preview(name = "Complete Two Tap (Design System V2)")
@Composable
fun CompleteTwoTapScreenPreviewDesignSystemV2() {
  PreviewWalletTheme {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(WalletTheme.colors.background)
    ) {
      FormScreen(
        model = CompleteTwoTapBodyModel(
          onBack = {},
          onContinue = {},
          onHelpClick = {},
          eventTrackerContext = PairHardwareEventTrackerScreenIdContext.ACCOUNT_CREATION
        )
      )
    }
  }
}
