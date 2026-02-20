package build.wallet.bitcoin.bdk

import bitkey.datadog.DatadogRumMonitorFake
import build.wallet.availability.NetworkConnection
import build.wallet.availability.NetworkReachability
import build.wallet.availability.NetworkReachabilityProviderMock
import build.wallet.bdk.bindings.BdkError
import build.wallet.bitcoin.BitcoinNetworkType.BITCOIN
import build.wallet.bitcoin.sync.ElectrumReachability.ElectrumReachabilityError
import build.wallet.bitcoin.sync.ElectrumReachabilityMock
import build.wallet.bitcoin.sync.ElectrumServer.Blockstream
import build.wallet.bitcoin.sync.ElectrumServerSetting.Default
import build.wallet.bitcoin.sync.ElectrumServerSettingProviderMock
import build.wallet.bitcoin.sync.F8eDefinedElectrumServerMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.platform.device.DeviceInfoProviderMock
import build.wallet.time.ClockFake
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import uniffi.bdk.NoPointer
import uniffi.bdk.Persister

class BdkWalletSyncerV2ImplTests : FunSpec({
  val reachabilityMock = ElectrumReachabilityMock(reachableResult = Ok(Unit))
  val datadogRumMonitor = DatadogRumMonitorFake(turbines::create)
  val deviceInfoProvider = DeviceInfoProviderMock()
  val electrumServerSettingProvider =
    ElectrumServerSettingProviderMock(
      turbines::create,
      initialSetting = Default(F8eDefinedElectrumServerMock)
    )
  val networkReachabilityProvider = NetworkReachabilityProviderMock("", turbines::create)
  val persister = Persister(NoPointer)

  lateinit var electrumClient: ElectrumClientFake
  lateinit var electrumClientFactory: ElectrumClientFactoryFake
  lateinit var walletSyncer: BdkWalletSyncerV2Impl

  beforeEach {
    reachabilityMock.reachableResult = Ok(Unit)
    electrumClient = ElectrumClientFake()
    electrumClientFactory = ElectrumClientFactoryFake(electrumClient)
    walletSyncer =
      BdkWalletSyncerV2Impl(
        clock = ClockFake(),
        datadogRumMonitor = datadogRumMonitor,
        deviceInfoProvider = deviceInfoProvider,
        electrumServerSettingProvider = electrumServerSettingProvider,
        electrumReachability = reachabilityMock,
        networkReachabilityProvider = networkReachabilityProvider,
        electrumClientProvider = ElectrumClientProviderFake(electrumClientFactory)
      )
  }

  test("uses full scan when wallet has no checkpoint") {
    val wallet = BdkWalletSyncerV2WalletFake(checkpointHeight = 0u)

    walletSyncer.sync(wallet, persister, BITCOIN)

    electrumClient.fullScanCalls.size.shouldBe(1)
    electrumClient.syncCalls.shouldBeEmpty()
    wallet.appliedUpdate.shouldBe(electrumClient.update)
    wallet.persistCalls.shouldBe(1)

    datadogRumMonitor.startResourceLoadingCalls.awaitItem().shouldBe("BDK Wallet Sync")
    datadogRumMonitor.stopResourceLoadingCalls.awaitItem().shouldBe("BDK Wallet Sync")

    networkReachabilityProvider.updateNetworkReachabilityForConnectionCalls.awaitItem()
      .shouldBeTypeOf<NetworkReachabilityProviderMock.UpdateNetworkReachabilityForConnectionParams>()
      .apply {
        connection.shouldBeTypeOf<NetworkConnection.ElectrumSyncerNetworkConnection>()
        reachability.shouldBe(NetworkReachability.REACHABLE)
      }
  }

  test("uses sync with revealed spks when wallet has checkpoint") {
    val wallet = BdkWalletSyncerV2WalletFake(checkpointHeight = 100u)

    walletSyncer.sync(wallet, persister, BITCOIN)

    electrumClient.syncCalls.size.shouldBe(1)
    electrumClient.fullScanCalls.shouldBeEmpty()

    datadogRumMonitor.startResourceLoadingCalls.awaitItem().shouldBe("BDK Wallet Sync")
    datadogRumMonitor.stopResourceLoadingCalls.awaitItem().shouldBe("BDK Wallet Sync")
    networkReachabilityProvider.updateNetworkReachabilityForConnectionCalls.awaitItem()
  }

  test("uses backup server when primary is unreachable") {
    reachabilityMock.reachableResult =
      Err(
        ElectrumReachabilityError.Unreachable(BdkError.Generic(null, null))
      )

    walletSyncer.sync(BdkWalletSyncerV2WalletFake(0u), persister, BITCOIN)

    datadogRumMonitor.addErrorCalls.awaitItem()
      .shouldBe("Error Connecting To Primary Electrum Server")
    datadogRumMonitor.startResourceLoadingCalls.awaitItem().shouldBe("BDK Wallet Sync")
    datadogRumMonitor.stopResourceLoadingCalls.awaitItem().shouldBe("BDK Wallet Sync")
    networkReachabilityProvider.updateNetworkReachabilityForConnectionCalls.awaitItem()

    electrumClientFactory.requestedUrls.last().shouldBe(
      Blockstream(BITCOIN, isAndroidEmulator = false).electrumServerDetails.url()
    )
  }
})
