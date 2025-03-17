package build.wallet.ui.app.inheritance.claims.start

import androidx.compose.ui.Modifier
import bitkey.notifications.NotificationChannel
import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.settings.full.notifications.EnabledState
import build.wallet.statemachine.settings.full.notifications.RecoveryChannelsSettingsFormBodyModel
import build.wallet.statemachine.settings.full.notifications.RecoveryChannelsSettingsFormItemModel
import build.wallet.statemachine.settings.full.notifications.Source
import io.kotest.core.spec.style.FunSpec

class StartClaimCriticalAlertsSnapshots : FunSpec({
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

  test("missing recovery methods for inheritance start claim") {
    paparazzi.snapshot {
      RecoveryChannelsSettingsFormBodyModel(
        source = Source.InheritanceStartClaim,
        missingRecoveryMethods = listOf(
          NotificationChannel.Push
        ),
        pushItem = pushItem,
        smsItem = smsItem,
        emailItem = emailItem,
        onBack = {},
        learnOnClick = {},
        continueOnClick = {}
      ).render(modifier = Modifier)
    }
  }
})
