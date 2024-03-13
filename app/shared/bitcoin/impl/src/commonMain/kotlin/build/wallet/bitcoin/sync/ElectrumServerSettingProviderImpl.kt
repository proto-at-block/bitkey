@file:OptIn(ExperimentalSettingsApi::class)

package build.wallet.bitcoin.sync

import build.wallet.bitcoin.BitcoinNetworkType.BITCOIN
import build.wallet.bitcoin.sync.ElectrumServer.F8eDefined
import build.wallet.bitcoin.sync.ElectrumServerPreferenceValue.Off
import build.wallet.bitcoin.sync.ElectrumServerPreferenceValue.On
import build.wallet.keybox.KeyboxDao
import build.wallet.keybox.config.TemplateFullAccountConfigDao
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.get
import com.russhwolf.settings.ExperimentalSettingsApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.flow.zip

class ElectrumServerSettingProviderImpl(
  private val keyboxDao: KeyboxDao,
  private val templateFullAccountConfigDao: TemplateFullAccountConfigDao,
  private val electrumServerDao: ElectrumServerConfigRepository,
) : ElectrumServerSettingProvider {
  override fun get(): Flow<ElectrumServerSetting> {
    return keyboxDao
      .activeKeybox()
      .map { activeKeyboxResult ->
        when (activeKeyboxResult) {
          is Err -> null
          is Ok ->
            when (val keybox = activeKeyboxResult.value) {
              null -> null
              else -> keybox.config.bitcoinNetworkType
            }
        }
      }
      .transform { activeNetwork ->
        // In the event the user has not persisted a custom Electrum server at all, we want to be
        // able to infer the network they are connected to, since Block-provided Electrum endpoints
        // would be network-aware for development purposes.
        val defaultNetwork =
          templateFullAccountConfigDao.config().first().get()?.bitcoinNetworkType ?: BITCOIN
        val networkToUse = activeNetwork ?: defaultNetwork

        electrumServerDao.getF8eDefinedElectrumServer()
          .zip(
            electrumServerDao.getUserElectrumServerPreference()
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
                  } ?: ElectrumServerSetting.Default(server = ElectrumServer.Mempool(networkToUse))
              }
            )
          }
      }
  }

  override suspend fun setUserDefinedServer(server: ElectrumServer) {
    electrumServerDao.storeUserPreference(
      preference = On(server)
    )
  }
}
