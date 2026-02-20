package build.wallet.bitcoin.bdk

import bitkey.datadog.DatadogRumMonitor
import bitkey.datadog.ErrorSource.Network
import bitkey.datadog.ResourceType.Other
import build.wallet.availability.NetworkConnection
import build.wallet.availability.NetworkReachability
import build.wallet.availability.NetworkReachabilityProvider
import build.wallet.bdk.bindings.BdkError
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.sync.ElectrumReachability
import build.wallet.bitcoin.sync.ElectrumServer
import build.wallet.bitcoin.sync.ElectrumServer.*
import build.wallet.bitcoin.sync.ElectrumServerSetting.Default
import build.wallet.bitcoin.sync.ElectrumServerSettingProvider
import build.wallet.catchingResult
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.logDebug
import build.wallet.logging.logFailure
import build.wallet.logging.logWarn
import build.wallet.platform.device.DeviceInfoProvider
import build.wallet.platform.device.DevicePlatform.Android
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.asTimeSource
import uniffi.bdk.ElectrumClient
import uniffi.bdk.FullScanRequestBuilder
import uniffi.bdk.Persister
import uniffi.bdk.SyncRequestBuilder
import uniffi.bdk.Update
import uniffi.bdk.Wallet
import kotlin.time.measureTimedValue

@BitkeyInject(AppScope::class)
class BdkWalletSyncerV2Impl(
  private val clock: Clock,
  private val datadogRumMonitor: DatadogRumMonitor,
  private val deviceInfoProvider: DeviceInfoProvider,
  private val electrumServerSettingProvider: ElectrumServerSettingProvider,
  private val electrumReachability: ElectrumReachability,
  private val networkReachabilityProvider: NetworkReachabilityProvider,
  private val electrumClientProvider: ElectrumClientProvider,
) : BdkWalletSyncerV2 {
  /**
   * A mutex used to ensure only one call to sync is in flight at a time.
   */
  private val syncLock = Mutex(locked = false)

  override suspend fun sync(
    bdkWallet: Wallet,
    persister: Persister,
    networkType: BitcoinNetworkType,
  ): Result<Unit, BdkError> =
    coroutineBinding {
      logDebug { "Attempting BDK 2 wallet sync..." }
      if (syncLock.isLocked) {
        return@coroutineBinding Unit
      }

      syncLock.withLock {
        val electrumServerSetting = electrumServerSettingProvider.get().first()
        var serverToTry: ElectrumServer? = null
        // Check Electrum reachability
        electrumReachability.reachable(electrumServerSetting.server)
          .onFailure {
            logWarn(throwable = it.cause) { "Error Connecting To Primary Electrum Server" }
            if (electrumServerSetting is Default) {
              datadogRumMonitor.addError(
                "Error Connecting To Primary Electrum Server",
                Network,
                mapOf("server" to electrumServerSetting.server.electrumServerDetails.url())
              )
              val deviceInfo = deviceInfoProvider.getDeviceInfo()
              val isAndroidEmulator = deviceInfo.devicePlatform == Android && deviceInfo.isEmulator
              serverToTry =
                when (val server = electrumServerSetting.server) {
                  is F8eDefined -> Blockstream(networkType, isAndroidEmulator)
                  is Blockstream -> Mempool(networkType, isAndroidEmulator)
                  is Mempool -> Blockstream(networkType, isAndroidEmulator)
                  is Custom -> server
                }
            }
          }

        val resourceKey = "BDK Wallet Sync"
        val rumAttributes = mapOf("bdk_version" to "2")
        when (electrumServerSetting) {
          is Default -> {
            val electrumUrl = (serverToTry ?: electrumServerSetting.server).electrumServerDetails.url()
            datadogRumMonitor.startResourceLoading(
              resourceKey = resourceKey,
              method = "GET", // We're required to pass an HTTP method, but this is an SSL connection.
              url = electrumUrl,
              attributes = rumAttributes
            )
          }

          else -> Unit
        }
        val electrumServer = serverToTry ?: electrumServerSetting.server
        val timedSyncResult =
          clock.asTimeSource().measureTimedValue {
            performSync(
              bdkWallet = bdkWallet,
              persister = persister,
              electrumServer = electrumServer
            )
          }

        timedSyncResult.value
          .map {
            when (electrumServerSetting) {
              is Default -> {
                datadogRumMonitor.stopResourceLoading(
                  resourceKey = resourceKey,
                  kind = Other,
                  attributes = rumAttributes
                )
              }

              else -> Unit
            }
          }
          .onFailure {
            if (electrumServerSetting is Default && it is BdkError.Electrum) {
              val electrumUrl = electrumServer.electrumServerDetails.url()
              datadogRumMonitor.addError(
                "Error connecting to Electrum",
                Network,
                mapOf("url" to electrumUrl)
              )
            }
          }
          .logFailure { "BDK2 sync failed" }
          .bind()
      }
    }

  private suspend fun performSync(
    bdkWallet: Wallet,
    persister: Persister,
    electrumServer: ElectrumServer,
  ): Result<Unit, BdkError> {
    val electrumUrl = electrumServer.electrumServerDetails.url()
    return catchingResult {
      electrumClientProvider.withClient(electrumUrl) { client ->
        val update = client.runScan(bdkWallet)
        bdkWallet.applyUpdate(update)
        bdkWallet.persist(persister)
        Unit
      }
    }
      .mapError { it.toBdkError() }
      .onSuccess {
        networkReachabilityProvider.updateNetworkReachabilityForConnection(
          connection = NetworkConnection.ElectrumSyncerNetworkConnection,
          reachability = NetworkReachability.REACHABLE
        )
      }
      .onFailure {
        if (it is BdkError.Electrum) {
          electrumClientProvider.invalidate(electrumUrl)
        }
        networkReachabilityProvider.updateNetworkReachabilityForConnection(
          connection = NetworkConnection.ElectrumSyncerNetworkConnection,
          reachability = NetworkReachability.UNREACHABLE
        )
      }
  }

  private fun ElectrumClient.runScan(wallet: Wallet): Update {
    return if (wallet.latestCheckpoint().height == 0u) {
      // No checkpoint yet; full scan to discover any previously used addresses.
      wallet.startFullScan()
        .buildRequest()
        .let { request ->
          fullScan(
            request = request,
            stopGap = DEFAULT_STOP_GAP,
            batchSize = DEFAULT_BATCH_SIZE,
            fetchPrevTxouts = FETCH_PREV_TXOUTS
          )
        }
    } else {
      wallet.startSyncWithRevealedSpks()
        .buildRequest()
        .let { request ->
          sync(
            request = request,
            batchSize = DEFAULT_BATCH_SIZE,
            fetchPrevTxouts = FETCH_PREV_TXOUTS
          )
        }
    }
  }

  private fun FullScanRequestBuilder.buildRequest() = build()

  private fun SyncRequestBuilder.buildRequest() = build()

  private companion object {
    const val DEFAULT_STOP_GAP: ULong = 1000u
    const val DEFAULT_BATCH_SIZE: ULong = 20u
    const val FETCH_PREV_TXOUTS: Boolean = true
  }
}
