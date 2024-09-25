package build.wallet.statemachine.settings.full.mobilepay

import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.analytics.events.screen.id.SettingsEventTrackerScreenId
import build.wallet.compose.collections.immutableListOfNotNull
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.limit.SpendingLimitsCopy
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
    spendingLimitCopy: SpendingLimitsCopy,
    disableAlertModel: ButtonAlertModel?,
    spendingLimitCardModel: SpendingLimitCardModel?,
  ) : this(
    onBack = onBack,
    switchCardModel = SwitchCardModel(
      title = spendingLimitCopy.title,
      subline = spendingLimitCopy.subline,
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
}

fun disableMobilePayAlertModel(
  title: String,
  subline: String,
  onConfirm: () -> Unit,
  onCancel: () -> Unit,
) = DisableAlertModel(
  title = title,
  subline = subline,
  onConfirm = onConfirm,
  onCancel = onCancel
)
