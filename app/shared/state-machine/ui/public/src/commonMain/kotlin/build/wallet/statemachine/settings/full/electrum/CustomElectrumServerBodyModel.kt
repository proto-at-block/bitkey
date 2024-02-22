package build.wallet.statemachine.settings.full.electrum

import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.analytics.events.screen.id.SettingsEventTrackerScreenId
import build.wallet.statemachine.core.BodyModel
import build.wallet.ui.model.alert.AlertModel
import build.wallet.ui.model.alert.DisableAlertModel
import build.wallet.ui.model.switch.SwitchCardModel
import build.wallet.ui.model.switch.SwitchCardModel.ActionRow
import build.wallet.ui.model.switch.SwitchModel
import kotlinx.collections.immutable.toImmutableList

data class CustomElectrumServerBodyModel(
  override val onBack: () -> Unit,
  val switchCardModel: SwitchCardModel,
  val disableAlertModel: AlertModel?,
  override val eventTrackerScreenInfo: EventTrackerScreenInfo? =
    EventTrackerScreenInfo(
      eventTrackerScreenId = SettingsEventTrackerScreenId.SETTINGS_CUSTOM_ELECTRUM_SERVER
    ),
) : BodyModel() {
  constructor(
    onBack: () -> Unit,
    switchIsChecked: Boolean,
    electrumServerRow: ActionRow?,
    onSwitchCheckedChange: (Boolean) -> Unit,
    disableAlertModel: AlertModel?,
  ) : this(
    onBack = onBack,
    switchCardModel =
      SwitchCardModel(
        title = "Custom Electrum Server",
        subline = "By default, Bitkey connects to Mempool’s Electrum Server in order to access the Bitcoin blockchain. You can choose to connect to your own Electrum Server any time.",
        switchModel =
          SwitchModel(
            checked = switchIsChecked,
            onCheckedChange = onSwitchCheckedChange
          ),
        actionRows = listOfNotNull(electrumServerRow).toImmutableList()
      ),
    disableAlertModel = disableAlertModel
  )
}

fun disableCustomElectrumServerAlertModel(
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) = DisableAlertModel(
  title = "Disable Custom Electrum Server?",
  subline = "You will be connecting to Mempool’s Electrum Server once you hit “Disable”",
  onConfirm = onConfirm,
  onCancel = onDismiss
)
