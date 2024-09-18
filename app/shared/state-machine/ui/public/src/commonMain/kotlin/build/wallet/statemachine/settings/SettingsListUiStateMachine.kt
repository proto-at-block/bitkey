package build.wallet.statemachine.settings

import build.wallet.f8e.F8eEnvironment
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.StateMachine
import build.wallet.ui.model.alert.ButtonAlertModel

/**
 * State machine for the List body model to be used in [SettingsHomeUiStateMachine] and
 * [LiteSettingsHomeUiStateMachine] when the list of all settings is showing.
 *
 * The parent state machines customize which rows to show and what happens when they're tapped.
 * This state machine handles arranging the rows into sections and defining their UI models.
 */
interface SettingsListUiStateMachine : StateMachine<SettingsListUiProps, BodyModel>

/**
 * @param supportedRows Set of rows supported by the caller.
 */
data class SettingsListUiProps(
  val onBack: () -> Unit,
  val f8eEnvironment: F8eEnvironment,
  val supportedRows: Set<SettingsListRow>,
  val onShowAlert: (ButtonAlertModel) -> Unit,
  val onDismissAlert: () -> Unit,
) {
  /**
   * A row to be shown in the settings list.
   */
  sealed interface SettingsListRow {
    val onClick: () -> Unit

    data class BitkeyDevice(override val onClick: () -> Unit) : SettingsListRow

    data class CustomElectrumServer(override val onClick: () -> Unit) : SettingsListRow

    data class CurrencyPreference(override val onClick: () -> Unit) : SettingsListRow

    data class HelpCenter(override val onClick: () -> Unit) : SettingsListRow

    data class MobilePay(override val onClick: () -> Unit) : SettingsListRow

    data class NotificationPreferences(override val onClick: () -> Unit) : SettingsListRow

    data class RecoveryChannels(override val onClick: () -> Unit) : SettingsListRow

    data class ContactUs(override val onClick: () -> Unit) : SettingsListRow

    data class TrustedContacts(override val onClick: () -> Unit) : SettingsListRow

    data class CloudBackupHealth(override val onClick: () -> Unit) : SettingsListRow

    data class RotateAuthKey(override val onClick: () -> Unit) : SettingsListRow

    data class DebugMenu(override val onClick: () -> Unit) : SettingsListRow

    data class Biometric(override val onClick: () -> Unit) : SettingsListRow

    data class UtxoConsolidation(override val onClick: () -> Unit) : SettingsListRow
  }
}
