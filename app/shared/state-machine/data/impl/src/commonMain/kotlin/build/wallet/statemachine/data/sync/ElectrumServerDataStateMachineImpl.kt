package build.wallet.statemachine.data.sync

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.bitcoin.BitcoinNetworkType.BITCOIN
import build.wallet.bitcoin.BitcoinNetworkType.REGTEST
import build.wallet.bitcoin.BitcoinNetworkType.SIGNET
import build.wallet.bitcoin.BitcoinNetworkType.TESTNET
import build.wallet.bitcoin.sync.ElectrumServer
import build.wallet.bitcoin.sync.ElectrumServerConfigRepository
import build.wallet.bitcoin.sync.ElectrumServerPreferenceValue
import build.wallet.bitcoin.sync.ElectrumServerPreferenceValue.Off
import build.wallet.bitcoin.sync.ElectrumServerPreferenceValue.On
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.f8e.configuration.GetBdkConfigurationF8eClient
import build.wallet.logging.LogLevel.Error
import build.wallet.logging.log
import build.wallet.statemachine.data.sync.ElectrumServerState.Loaded
import build.wallet.statemachine.data.sync.ElectrumServerState.RetrievingFromF8e
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.flatMap
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.launch

class ElectrumServerDataStateMachineImpl(
  val electrumServerRepository: ElectrumServerConfigRepository,
  val getBdkConfigurationF8eClient: GetBdkConfigurationF8eClient,
) : ElectrumServerDataStateMachine {
  @Composable
  override fun model(props: ElectrumServerDataProps): ElectrumServerData {
    var state: ElectrumServerState by remember { mutableStateOf(RetrievingFromF8e) }
    val persistedElectrumServer = rememberF8eProvidedElectrumServerData(props)
    val persistedUserCustomElectrumServerPreferenceValue =
      rememberUserCustomElectrumServerPreferenceValue()

    val scope = rememberStableCoroutineScope()

    LaunchedEffect("get-bdk-config", props.network) {
      syncBdkConfig(props).onSuccess { state = Loaded(it) }
    }

    return when (val serverState = state) {
      is RetrievingFromF8e ->
        ElectrumServerData(
          defaultElectrumServer = persistedElectrumServer,
          userDefinedElectrumServerPreferenceValue = persistedUserCustomElectrumServerPreferenceValue,
          disableCustomElectrumServer = {
            scope.launch { restoreDefaultServer(it) }
          }
        )

      is Loaded ->
        ElectrumServerData(
          defaultElectrumServer = serverState.electrumServer,
          userDefinedElectrumServerPreferenceValue = persistedUserCustomElectrumServerPreferenceValue,
          disableCustomElectrumServer = {
            scope.launch { restoreDefaultServer(it) }
          }
        )
    }
  }

  private suspend fun syncBdkConfig(
    props: ElectrumServerDataProps,
  ): Result<ElectrumServer.F8eDefined, ElectrumServerLookupError> =
    getBdkConfigurationF8eClient.getConfiguration(
      f8eEnvironment = props.f8eEnvironment
    )
      .mapError { ElectrumServerLookupError(it.message, it) }
      .flatMap {
        if (props.network == REGTEST && it.regtest == null) {
          val msg = "server did not return regtest electrum details, but regtest network was selected"
          log(Error) { msg }
          return@flatMap Err(ElectrumServerLookupError(msg, null))
        }
        val electrumServer =
          when (props.network) {
            BITCOIN -> it.mainnet
            SIGNET -> it.signet
            TESTNET -> it.testnet
            REGTEST -> requireNotNull(it.regtest)
          }
        electrumServerRepository.storeF8eDefinedElectrumConfig(electrumServer.electrumServerDetails)
        return Ok(ElectrumServer.F8eDefined(electrumServer.electrumServerDetails))
      }

  @Composable
  private fun rememberF8eProvidedElectrumServerData(
    props: ElectrumServerDataProps,
  ): ElectrumServer {
    return remember {
      electrumServerRepository.getF8eDefinedElectrumServer()
    }.collectAsState(null).value ?: ElectrumServer.Mempool(props.network)
  }

  @Composable
  private fun rememberUserCustomElectrumServerPreferenceValue(): ElectrumServerPreferenceValue {
    return remember { electrumServerRepository.getUserElectrumServerPreference() }
      .collectAsState(null).value ?: Off(
      previousUserDefinedElectrumServer = null
    )
  }

  private suspend fun restoreDefaultServer(
    previousElectrumServerPreferenceValue: ElectrumServerPreferenceValue,
  ) {
    when (previousElectrumServerPreferenceValue) {
      is On -> {
        electrumServerRepository.storeUserPreference(
          preference =
            Off(
              previousUserDefinedElectrumServer = previousElectrumServerPreferenceValue.server
            )
        )
      }

      else -> Unit
    }
  }
}

private sealed interface ElectrumServerState {
  data object RetrievingFromF8e : ElectrumServerState

  data class Loaded(
    val electrumServer: ElectrumServer,
  ) : ElectrumServerState
}

private data class ElectrumServerLookupError(
  override val message: String?,
  override val cause: Throwable?,
) : Exception(message, cause)
