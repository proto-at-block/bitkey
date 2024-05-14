package build.wallet.bitcoin.bdk

import build.wallet.availability.NetworkConnection
import build.wallet.availability.NetworkReachability
import build.wallet.availability.NetworkReachabilityProvider
import build.wallet.bdk.bindings.BdkError
import build.wallet.bdk.bindings.BdkWallet
import build.wallet.bdk.bindings.sync
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.sync.ElectrumReachability
import build.wallet.bitcoin.sync.ElectrumServer
import build.wallet.bitcoin.sync.ElectrumServer.Blockstream
import build.wallet.bitcoin.sync.ElectrumServer.Custom
import build.wallet.bitcoin.sync.ElectrumServer.F8eDefined
import build.wallet.bitcoin.sync.ElectrumServer.Mempool
import build.wallet.bitcoin.sync.ElectrumServerSetting.Default
import build.wallet.bitcoin.sync.ElectrumServerSettingProvider
import build.wallet.datadog.DatadogRumMonitor
import build.wallet.datadog.ErrorSource.Network
import build.wallet.datadog.ResourceType.Other
import build.wallet.logging.LogLevel.Info
import build.wallet.logging.LogLevel.Warn
import build.wallet.logging.log
import build.wallet.logging.logFailure
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.binding.binding
import com.github.michaelbull.result.flatMap
import com.github.michaelbull.result.map
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.asTimeSource
import kotlin.time.TimedValue
import kotlin.time.measureTimedValue

class BdkWalletSyncerImpl(
  private val bdkBlockchainProvider: BdkBlockchainProvider,
  private val clock: Clock,
  private val datadogRumMonitor: DatadogRumMonitor,
  private val electrumServerSettingProvider: ElectrumServerSettingProvider,
  val electrumReachability: ElectrumReachability,
  private val networkReachabilityProvider: NetworkReachabilityProvider,
) : BdkWalletSyncer {
  /**
   * A mutex used to ensure only one call to sync is in flight at a time
   * and to record unusually long syncs.
   */
  private val syncLock = Mutex(locked = false)

  override suspend fun sync(
    bdkWallet: BdkWallet,
    networkType: BitcoinNetworkType,
  ): Result<Unit, BdkError> =
    binding {
      log { "Attempting wallet sync..." }
      // Ignore a request if there is already a sync in progress
      if (syncLock.isLocked) {
        log(Warn) { "Requested sync when one was in progress, ignoring" }
        Ok(Unit).bind()
      }

      syncLock.withLock {
        val electrumServerSetting = electrumServerSettingProvider.get().first()

        var serverToTry: ElectrumServer? = null
        // Check Electrum reachability
        electrumReachability.reachable(electrumServerSetting.server, networkType)
          .onFailure {
            log(Warn, throwable = it.cause) { "Error Connecting To Primary Electrum Server" }
            if (electrumServerSetting is Default) {
              datadogRumMonitor.addError(
                "Error Connecting To Primary Electrum Server",
                Network,
                mapOf("server" to electrumServerSetting.server.electrumServerDetails.url())
              )
              serverToTry =
                when (val server = electrumServerSetting.server) {
                  is F8eDefined -> Blockstream(networkType)
                  is Blockstream -> Mempool(networkType)
                  is Mempool -> Blockstream(networkType)
                  is Custom -> server
                }
            }
          }

        val resourceKey = "BDK Wallet Sync"
        when (electrumServerSetting) {
          is Default -> {
            val electrumUrl = electrumServerSetting.server.electrumServerDetails.url()
            datadogRumMonitor.startResourceLoading(
              resourceKey = resourceKey,
              method = "GET", // We're required to pass an HTTP method, but this is an SSL connection.
              url = electrumUrl,
              attributes = emptyMap()
            )
          }
          else -> Unit
        }

        val timedSyncResult: TimedValue<Result<Unit, BdkError>> =
          clock.asTimeSource().measureTimedValue {
            performSync(
              bdkWallet = bdkWallet,
              overriddenElectrumServer = serverToTry
            )
          }

        timedSyncResult.value
          .map {
            when (electrumServerSetting) {
              is Default -> {
                datadogRumMonitor.stopResourceLoading(
                  resourceKey = resourceKey,
                  kind = Other,
                  attributes = emptyMap()
                )
              }
              else -> Unit
            }
          }
          .onFailure {
            if (electrumServerSetting is Default && it is BdkError.Electrum) {
              val electrumUrl = electrumServerSetting.server.electrumServerDetails.url()
              datadogRumMonitor.addError(
                "Error connecting to Electrum",
                Network,
                mapOf("url" to electrumUrl)
              )
            }
          }
          .logFailure { "Failed to sync BDK wallet" }
          .bind()
      }
    }

  private suspend fun performSync(
    bdkWallet: BdkWallet,
    overriddenElectrumServer: ElectrumServer?,
  ): Result<Unit, BdkError> {
    return bdkBlockchainProvider
      .blockchain(
        electrumServer = overriddenElectrumServer
      )
      .result
      .flatMap { blockchain ->
        bdkWallet
          .sync(
            blockchain = blockchain,
            progress = null
          )
          .result
      }
      .logFailure(Warn) { "Error syncing BDK wallet" }
      .onSuccess {
        log(Info) { "Wallet sync complete" }
        networkReachabilityProvider.updateNetworkReachabilityForConnection(
          connection = NetworkConnection.ElectrumSyncerNetworkConnection,
          reachability = NetworkReachability.REACHABLE
        )
      }
      .onFailure {
        networkReachabilityProvider.updateNetworkReachabilityForConnection(
          connection = NetworkConnection.ElectrumSyncerNetworkConnection,
          reachability = NetworkReachability.UNREACHABLE
        )
      }
  }
}
