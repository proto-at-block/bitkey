package build.wallet.statemachine.data.app

import build.wallet.emergencyaccesskit.EmergencyAccessKitAssociation
import build.wallet.money.display.CurrencyPreferenceData
import build.wallet.statemachine.data.firmware.FirmwareData
import build.wallet.statemachine.data.keybox.AccountData
import build.wallet.statemachine.data.lightning.LightningNodeData
import build.wallet.statemachine.data.sync.ElectrumServerData

/**
 * Describes app-scoped data.
 */
sealed interface AppData {
  /**
   * App-scoped data is loaded.
   */
  data object LoadingAppData : AppData

  /**
   * App-scoped data is loaded.
   *
   * @property accountData keybox data (no keybox, has keybox, etc).
   * @property lightningNodeData data corresponding to the LDK node. Currently app-scoped, but in the future might need to be
   * scoped to keybox data.
   * @property electrumServerData data describing Electrum nodes app should connect to.
   * @property firmwareData provides data for the firmware of the last used HW device, if any
   */
  data class AppLoadedData(
    val accountData: AccountData,
    val lightningNodeData: LightningNodeData,
    val electrumServerData: ElectrumServerData,
    val firmwareData: FirmwareData,
    val currencyPreferenceData: CurrencyPreferenceData,
    val eakAssociation: EmergencyAccessKitAssociation,
  ) : AppData
}
