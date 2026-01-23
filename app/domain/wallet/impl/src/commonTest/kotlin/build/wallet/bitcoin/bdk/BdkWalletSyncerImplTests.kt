package build.wallet.bitcoin.bdk

import bitkey.datadog.DatadogRumMonitorFake
import build.wallet.availability.NetworkConnection
import build.wallet.availability.NetworkReachability
import build.wallet.availability.NetworkReachabilityProviderMock
import build.wallet.bdk.bindings.BdkError
import build.wallet.bdk.bindings.BdkResult
import build.wallet.bitcoin.BitcoinNetworkType.BITCOIN
import build.wallet.bitcoin.sync.ElectrumReachability.ElectrumReachabilityError
import build.wallet.bitcoin.sync.ElectrumReachabilityMock
import build.wallet.bitcoin.sync.ElectrumServer
import build.wallet.bitcoin.sync.ElectrumServer.Blockstream
import build.wallet.bitcoin.sync.ElectrumServerDetails
import build.wallet.bitcoin.sync.ElectrumServerSetting.Default
import build.wallet.bitcoin.sync.ElectrumServerSettingProviderMock
import build.wallet.bitcoin.sync.F8eDefinedElectrumServerMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.platform.device.DeviceInfoProviderMock
import build.wallet.time.ClockFake
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf

class BdkWalletSyncerImplTests : FunSpec({
  val reachabilityMock = ElectrumReachabilityMock(reachableResult = Ok(Unit))
  val bdkBlockchainProvider = BdkBlockchainProviderMock(turbines::create)
  val datadogRumMonitor = DatadogRumMonitorFake(turbines::create)
  val deviceInfoProvider = DeviceInfoProviderMock()
  val electrumServerSettingProvider =
    ElectrumServerSettingProviderMock(
      turbines::create,
      initialSetting = Default(F8eDefinedElectrumServerMock)
    )
  val networkReachabilityProvider = NetworkReachabilityProviderMock("", turbines::create)

  val bdkWallet = BdkWalletMock(turbines::create)
  lateinit var walletSyncer: BdkWalletSyncerImpl

  beforeEach {
    reachabilityMock.reachableResult = Ok(Unit)
  }

  context("Reachable server") {
    beforeEach {
      walletSyncer =
        BdkWalletSyncerImpl(
          bdkBlockchainProvider = bdkBlockchainProvider,
          clock = ClockFake(),
          datadogRumMonitor = datadogRumMonitor,
          deviceInfoProvider = deviceInfoProvider,
          electrumServerSettingProvider = electrumServerSettingProvider,
          electrumReachability = reachabilityMock,
          networkReachabilityProvider = networkReachabilityProvider
        )
    }

    test("Should use reachable server") {
      walletSyncer.sync(bdkWallet, BITCOIN)

      datadogRumMonitor.startResourceLoadingCalls.awaitItem().shouldBe("BDK Wallet Sync")
      bdkBlockchainProvider.legacyBlockchainCalls.awaitItem().shouldBeNull()
      datadogRumMonitor.stopResourceLoadingCalls.awaitItem().shouldBe("BDK Wallet Sync")

      networkReachabilityProvider.updateNetworkReachabilityForConnectionCalls.awaitItem()
        .shouldBeTypeOf<NetworkReachabilityProviderMock.UpdateNetworkReachabilityForConnectionParams>()
        .apply {
          connection.shouldBeTypeOf<NetworkConnection.ElectrumSyncerNetworkConnection>()
          reachability.shouldBe(NetworkReachability.REACHABLE)
        }
    }
  }

  context("Unreachable server") {
    beforeEach {
      walletSyncer =
        BdkWalletSyncerImpl(
          bdkBlockchainProvider = bdkBlockchainProvider,
          clock = ClockFake(),
          datadogRumMonitor = datadogRumMonitor,
          deviceInfoProvider = deviceInfoProvider,
          electrumServerSettingProvider = electrumServerSettingProvider,
          electrumReachability =
            ElectrumReachabilityMock(
              reachableResult =
                Err(
                  ElectrumReachabilityError.Unreachable(BdkError.Generic(null, null))
                )
            ),
          networkReachabilityProvider = networkReachabilityProvider
        )

      electrumServerSettingProvider.reset()
    }

    test("Should use backup") {
      walletSyncer.sync(bdkWallet, BITCOIN)

      datadogRumMonitor.addErrorCalls.awaitItem().shouldBe(
        "Error Connecting To Primary Electrum Server"
      )

      datadogRumMonitor.startResourceLoadingCalls.awaitItem().shouldBe("BDK Wallet Sync")
      (bdkBlockchainProvider.legacyBlockchainCalls.awaitItem() as ElectrumServer).shouldBe(
        Blockstream(BITCOIN, isAndroidEmulator = false)
      )
      datadogRumMonitor.stopResourceLoadingCalls.awaitItem().shouldBe("BDK Wallet Sync")

      networkReachabilityProvider.updateNetworkReachabilityForConnectionCalls.awaitItem()
        .shouldBeTypeOf<NetworkReachabilityProviderMock.UpdateNetworkReachabilityForConnectionParams>()
        .apply {
          connection.shouldBeTypeOf<NetworkConnection.ElectrumSyncerNetworkConnection>()
          reachability.shouldBe(NetworkReachability.REACHABLE)
        }
    }

    test("Custom Electrum server should not use backup, and shouldn't log in RUM") {
      // We set a custom Electrum server
      electrumServerSettingProvider.setUserDefinedServer(
        ElectrumServer.Custom(
          ElectrumServerDetails("chicken.info", "50002")
        )
      )
      electrumServerSettingProvider.setCalls.awaitItem()

      bdkBlockchainProvider.legacyBlockchainResult = BdkResult.Err(BdkError.Generic(null, null))
      walletSyncer.sync(bdkWallet, BITCOIN)
      // We did not override to use secondary Electrum server
      bdkBlockchainProvider.legacyBlockchainCalls.awaitItem().shouldBeNull()

      networkReachabilityProvider.updateNetworkReachabilityForConnectionCalls.awaitItem()
        .shouldBeTypeOf<NetworkReachabilityProviderMock.UpdateNetworkReachabilityForConnectionParams>()
        .apply {
          connection.shouldBeTypeOf<NetworkConnection.ElectrumSyncerNetworkConnection>()
          reachability.shouldBe(NetworkReachability.UNREACHABLE)
        }
    }
  }
})
