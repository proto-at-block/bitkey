package build.wallet.bitcoin.sync

import build.wallet.bitcoin.sync.ElectrumServer.F8eDefined
import build.wallet.bitcoin.sync.ElectrumServerPreferenceValue.Off
import build.wallet.bitcoin.sync.ElectrumServerPreferenceValue.On
import build.wallet.debug.DebugOptionsService
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.keybox.KeyboxDao
import com.github.michaelbull.result.mapBoth
import kotlinx.coroutines.flow.*

@BitkeyInject(AppScope::class)
class ElectrumServerSettingProviderImpl(
  private val keyboxDao: KeyboxDao,
  private val debugOptionsService: DebugOptionsService,
  private val electrumServerConfigRepository: ElectrumServerConfigRepository,
) : ElectrumServerSettingProvider {
  override fun get(): Flow<ElectrumServerSetting> {
    return keyboxDao
      .activeKeybox()
      .map { activeKeyboxResult ->
        activeKeyboxResult
          .mapBoth(
            success = {
              when (val keybox = activeKeyboxResult.value) {
                null -> null
                else -> keybox.config.bitcoinNetworkType
              }
            },
            failure = { null }
          )
      }
      .transform { activeNetwork ->
        // In the event the user has not persisted a custom Electrum server at all, we want to be
        // able to infer the network they are connected to, since Block-provided Electrum endpoints
        // would be network-aware for development purposes.
        val defaultNetwork = debugOptionsService.options().first().bitcoinNetworkType
        val networkToUse = activeNetwork ?: defaultNetwork

        electrumServerConfigRepository.getF8eDefinedElectrumServer()
          .zip(
            electrumServerConfigRepository.getUserElectrumServerPreference()
          ) { f8eDefault, preference -> Pair(f8eDefault, preference) }
          .collect { (defaultServer, preference) ->
            emit(
              when (preference) {
                // IF has custom preference
                //     RETURN user-defined Electrum server settings
                is On -> ElectrumServerSetting.UserDefined(preference.server)

                // ELSE
                //     IF F8eElectrumServerKeyValueStore has host information
                //         RETURN F8eElectrumServerKeyValueStore host information
                //     ELSE
                //         RETURN Mempool server
                // END
                is Off, null ->
                  defaultServer?.let {
                    ElectrumServerSetting.Default(F8eDefined(it.electrumServerDetails))
                  } ?: ElectrumServerSetting.Default(server = ElectrumServer.Mempool(networkToUse, isAndroidEmulator = false))
              }
            )
          }
      }
  }

  override suspend fun setUserDefinedServer(server: ElectrumServer) {
    electrumServerConfigRepository.storeUserPreference(
      preference = On(server)
    )
  }
}
