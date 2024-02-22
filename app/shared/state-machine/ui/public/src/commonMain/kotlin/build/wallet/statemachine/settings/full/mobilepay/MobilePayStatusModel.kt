package build.wallet.statemachine.settings.full.mobilepay

import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.analytics.events.screen.id.SettingsEventTrackerScreenId
import build.wallet.statemachine.core.BodyModel
import build.wallet.ui.model.alert.AlertModel
import build.wallet.ui.model.alert.DisableAlertModel
import build.wallet.ui.model.switch.SwitchCardModel
import build.wallet.ui.model.switch.SwitchCardModel.ActionRow
import build.wallet.ui.model.switch.SwitchModel
import kotlinx.collections.immutable.toImmutableList

data class MobilePayStatusModel(
  override val onBack: () -> Unit,
  val switchCardModel: SwitchCardModel,
  val disableAlertModel: AlertModel?,
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
    disableAlertModel: AlertModel?,
    spendingLimitCardModel: SpendingLimitCardModel?,
  ) : this(
    onBack = onBack,
    switchCardModel =
      SwitchCardModel(
        title = "Mobile Pay",
        subline = "Leave your device at home, and make small spends with just the key on your phone.",
        switchModel =
          SwitchModel(
            checked = switchIsChecked,
            onCheckedChange = onSwitchCheckedChange
          ),
        actionRows = listOfNotNull(dailyLimitRow).toImmutableList()
      ),
    disableAlertModel = disableAlertModel,
    spendingLimitCardModel = spendingLimitCardModel
  )
}

fun disableMobilePayAlertModel(
  onConfirm: () -> Unit,
  onCancel: () -> Unit,
) = DisableAlertModel(
  title = "Disable mobile pay?",
  subline = "Turning it back on will require your Bitkey device",
  onConfirm = onConfirm,
  onCancel = onCancel
)
