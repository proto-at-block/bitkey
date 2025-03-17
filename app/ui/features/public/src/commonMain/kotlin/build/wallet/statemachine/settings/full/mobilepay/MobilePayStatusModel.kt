package build.wallet.statemachine.settings.full.mobilepay

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.analytics.events.screen.id.SettingsEventTrackerScreenId
import build.wallet.compose.collections.immutableListOfNotNull
import build.wallet.statemachine.core.BodyModel
import build.wallet.ui.app.mobilepay.MobilePayStatusScreen
import build.wallet.ui.model.alert.ButtonAlertModel
import build.wallet.ui.model.alert.DisableAlertModel
import build.wallet.ui.model.switch.SwitchCardModel
import build.wallet.ui.model.switch.SwitchCardModel.ActionRow
import build.wallet.ui.model.switch.SwitchModel

data class MobilePayStatusModel(
  override val onBack: () -> Unit,
  val switchCardModel: SwitchCardModel,
  val disableAlertModel: ButtonAlertModel?,
  val spendingLimitCardModel: SpendingLimitCardModel?,
  override val eventTrackerScreenInfo: EventTrackerScreenInfo? =
    EventTrackerScreenInfo(
      eventTrackerScreenId = SettingsEventTrackerScreenId.SETTINGS_MOBILE_PAY
    ),
) : BodyModel() {
  constructor(
    onBack: () -> Unit,
    switchIsChecked: Boolean,
    onSwitchCheckedChange: (Boolean) -> Unit,
    dailyLimitRow: ActionRow?,
    disableAlertModel: ButtonAlertModel?,
    spendingLimitCardModel: SpendingLimitCardModel?,
  ) : this(
    onBack = onBack,
    switchCardModel = SwitchCardModel(
      title = "Transfer without hardware",
      subline = "When on, you can spend up to a set daily limit without your Bitkey device.",
      switchModel =
        SwitchModel(
          checked = switchIsChecked,
          onCheckedChange = onSwitchCheckedChange
        ),
      actionRows = immutableListOfNotNull(dailyLimitRow)
    ),
    disableAlertModel = disableAlertModel,
    spendingLimitCardModel = spendingLimitCardModel
  )

  @Composable
  override fun render(modifier: Modifier) {
    MobilePayStatusScreen(modifier, model = this)
  }
}

fun disableMobilePayAlertModel(
  title: String,
  subline: String,
  primaryButtonText: String,
  cancelText: String,
  onConfirm: () -> Unit,
  onCancel: () -> Unit,
) = DisableAlertModel(
  title = title,
  subline = subline,
  primaryButtonText = primaryButtonText,
  secondaryButtonText = cancelText,
  onConfirm = onConfirm,
  onCancel = onCancel
)
