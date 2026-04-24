package build.wallet.ui.app.settings.notifications

import androidx.compose.ui.Modifier
import bitkey.notifications.NotificationChannel
import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.settings.full.notifications.EnabledState
import build.wallet.statemachine.settings.full.notifications.RecoveryChannelsSettingsFormBodyModel
import build.wallet.statemachine.settings.full.notifications.RecoveryChannelsSettingsFormItemModel
import build.wallet.statemachine.settings.full.notifications.Source
import io.kotest.core.spec.style.FunSpec

class CriticalAlertsSettingsScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  val emailItem = RecoveryChannelsSettingsFormItemModel(
    displayValue = "test@example.com",
    enabled = EnabledState.Enabled,
    uiErrorHint = null,
    onClick = {}
  )

  val smsItem = RecoveryChannelsSettingsFormItemModel(
    enabled = EnabledState.Disabled,
    uiErrorHint = null,
    onClick = {}
  )

  val pushItem = RecoveryChannelsSettingsFormItemModel(
    enabled = EnabledState.Disabled,
    uiErrorHint = null,
    onClick = {}
  )

  test("critical alerts settings dsv2") {
    paparazzi.snapshot(designSystemUpdatesEnabled = true) {
      RecoveryChannelsSettingsFormBodyModel(
        source = Source.Settings,
        missingRecoveryMethods = listOf(
          NotificationChannel.Sms,
          NotificationChannel.Push
        ),
        pushItem = pushItem,
        smsItem = smsItem,
        emailItem = emailItem,
        onBack = {},
        learnOnClick = {},
        continueOnClick = null,
        isDesignSystemV2Enabled = true
      ).render(modifier = Modifier)
    }
  }
})
