package build.wallet.bitcoin.sync

import kotlinx.coroutines.flow.Flow

/**
 * Local preference to store the user's defined endpoint for Electrum.
 */
interface ElectrumServerSettingProvider {
  /**
   * Emits an [ElectrumServerSetting] based on a few criteria:
   * 1. If the user has custom Electrum server setting turned on.
   * 2. If the user has previously defined some custom Electrum server.
   *
   * See code comments for additional nuance.
   */
  fun get(): Flow<ElectrumServerSetting>

  /**
   * Persists user-defined ElectrumServer.
   */
  suspend fun setUserDefinedServer(server: ElectrumServer)
}
