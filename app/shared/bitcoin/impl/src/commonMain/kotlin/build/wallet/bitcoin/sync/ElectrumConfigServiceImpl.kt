package build.wallet.bitcoin.sync

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.BitcoinNetworkType.*
import build.wallet.bitcoin.sync.ElectrumServerPreferenceValue.Off
import build.wallet.bitcoin.sync.ElectrumServerPreferenceValue.On
import build.wallet.debug.DebugOptionsService
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.configuration.GetBdkConfigurationF8eClient
import build.wallet.logging.LogLevel
import build.wallet.logging.log
import com.github.michaelbull.result.*
import com.github.michaelbull.result.coroutines.coroutineBinding
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch

class ElectrumConfigServiceImpl(
  private val electrumServerConfigRepository: ElectrumServerConfigRepository,
  private val debugOptionsService: DebugOptionsService,
  private val getBdkConfigurationF8eClient: GetBdkConfigurationF8eClient,
) : ElectrumConfigService, ElectrumServerConfigSyncWorker {
  override suspend fun executeWork() {
    coroutineScope {
      // Re-sync whenever the bitcoin network type or f8e environment change.
      launch {
        debugOptionsService.options()
          .mapLatest { options ->
            Pair(options.bitcoinNetworkType, options.f8eEnvironment)
          }
          .distinctUntilChanged()
          .collect {
            syncF8eDefinedElectrumConfig(it.first, it.second)
          }
      }
    }
  }

  override fun electrumServerPreference(): Flow<ElectrumServerPreferenceValue?> =
    electrumServerConfigRepository.getUserElectrumServerPreference()

  override suspend fun disableCustomElectrumServer(): Result<Unit, Error> {
    val previousUserDefinedElectrumServer =
      electrumServerConfigRepository.getUserElectrumServerPreference()
        .first()

    return coroutineBinding {
      when (previousUserDefinedElectrumServer) {
        is On -> {
          electrumServerConfigRepository.storeUserPreference(
            preference =
              Off(
                previousUserDefinedElectrumServer = previousUserDefinedElectrumServer.server
              )
          )
            .bind()
        }
        is Off, null -> Unit // Already disabled, nothing to do.
      }
    }
  }

  private suspend fun syncF8eDefinedElectrumConfig(
    bitcoinNetworkType: BitcoinNetworkType,
    f8eEnvironment: F8eEnvironment,
  ): Result<ElectrumServer.F8eDefined, ElectrumServerLookupError> {
    return getBdkConfigurationF8eClient.getConfiguration(
      f8eEnvironment = f8eEnvironment
    )
      .mapError { ElectrumServerLookupError(it.message, it) }
      .flatMap {
        if (bitcoinNetworkType == REGTEST && it.regtest == null) {
          val msg =
            "server did not return regtest electrum details, but regtest network was selected"
          log(LogLevel.Error) { msg }
          return@flatMap Err(ElectrumServerLookupError(msg, null))
        }
        val electrumServer = when (bitcoinNetworkType) {
          BITCOIN -> it.mainnet
          SIGNET -> it.signet
          TESTNET -> it.testnet
          REGTEST -> requireNotNull(it.regtest)
        }
        electrumServerConfigRepository.storeF8eDefinedElectrumConfig(electrumServer.electrumServerDetails)
        return Ok(ElectrumServer.F8eDefined(electrumServer.electrumServerDetails))
      }
  }
}

private data class ElectrumServerLookupError(
  override val message: String?,
  override val cause: Throwable?,
) : Exception(message, cause)
