package build.wallet.ui.app.settings.notifications

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.statemachine.notifications.NotificationPreferenceFormBodyModel
import build.wallet.statemachine.notifications.NotificationPreferencesFormEditingState
import build.wallet.ui.app.core.form.FormScreen
import build.wallet.ui.tooling.PreviewWalletTheme

@Preview(name = "Notification Preferences (Design System V2)")
@Composable
fun NotificationPreferencesFormScreenDesignSystemV2Preview() {
  PreviewWalletTheme {
    FormScreen(
      model = NotificationPreferenceFormBodyModel(
        transactionPush = false,
        updatesPush = true,
        updatesEmail = false,
        onTransactionPushToggle = {},
        onUpdatesPushToggle = {},
        onUpdatesEmailToggle = {},
        formEditingState = NotificationPreferencesFormEditingState.Editing,
        onBack = {},
        continueOnClick = {},
        onMoneyMovementLearnMore = {}
      )
    )
  }
}
