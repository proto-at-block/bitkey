package build.wallet.statemachine.data.app

import build.wallet.statemachine.data.firmware.FirmwareData
import build.wallet.statemachine.data.keybox.AccountData
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
   * @property electrumServerData data describing Electrum nodes app should connect to.
   * @property firmwareData provides data for the firmware of the last used HW device, if any
   */
  data class AppLoadedData(
    val accountData: AccountData,
    val electrumServerData: ElectrumServerData,
    val firmwareData: FirmwareData,
  ) : AppData
}
