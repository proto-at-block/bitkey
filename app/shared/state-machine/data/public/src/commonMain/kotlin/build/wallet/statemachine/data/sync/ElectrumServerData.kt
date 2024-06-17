package build.wallet.statemachine.data.sync

import build.wallet.bitcoin.sync.ElectrumServer
import build.wallet.bitcoin.sync.ElectrumServerPreferenceValue

/**
 * Describes information about the Electrum servers to use.
 */
data class ElectrumServerData(
  /**
   * Default Electrum server that the app should use if a user does not have a custom one defined.
   *
   * Prior to the initial API call, this value is set to Mempool's servers, depending on the
   * keybox's config data. Else, it will use the values returned by F8e from
   * [GetBdkConfigurationF8eClient]
   *
   */
  val defaultElectrumServer: ElectrumServer,
  /**
   * A user's preference of Electrum server to connect to.
   *
   * If no preference has been set prior, this value returns the `Off` variant of
   * `ElectrumServerPreferenceValue`.
   *
   */
  val userDefinedElectrumServerPreferenceValue: ElectrumServerPreferenceValue,
  /**
   * Disables the Custom Electrum Server feature.
   */
  val disableCustomElectrumServer: (
    previousElectrumServerPreferenceValue: ElectrumServerPreferenceValue,
  ) -> Unit,
)
