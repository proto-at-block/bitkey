package build.wallet.bitcoin.sync

import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow

/**
 * Manages configuration details about what Electrum servers to use
 */
interface ElectrumConfigService {
  /**
   * Returns a flow of [ElectrumServerPreferenceValue], which is a user-defined preference for
   * using a custom Electrum server.
   */
  fun electrumServerPreference(): Flow<ElectrumServerPreferenceValue?>

  /**
   * Disables the custom electrum server feature; any previously configured [ElectrumServerPreferenceValue]
   * will be retained, but turned [ElectrumServerPreferenceValue.Off].
   */
  suspend fun disableCustomElectrumServer(): Result<Unit, Error>
}
