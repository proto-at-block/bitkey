package build.wallet.bitcoin.bdk

import bitkey.datadog.DatadogRumMonitorFake
import build.wallet.availability.NetworkConnection
import build.wallet.availability.NetworkReachability
import build.wallet.availability.NetworkReachabilityProviderMock
import build.wallet.bdk.bindings.BdkAddressIndex
import build.wallet.bdk.bindings.BdkAddressInfo
import build.wallet.bdk.bindings.BdkAddressMock
import build.wallet.bdk.bindings.BdkBalance
import build.wallet.bdk.bindings.BdkBlockchain
import build.wallet.bdk.bindings.BdkError
import build.wallet.bdk.bindings.BdkPartiallySignedTransaction
import build.wallet.bdk.bindings.BdkProgress
import build.wallet.bdk.bindings.BdkResult
import build.wallet.bdk.bindings.BdkScript
import build.wallet.bdk.bindings.BdkTransactionDetails
import build.wallet.bdk.bindings.BdkUtxo
import build.wallet.bdk.bindings.BdkWallet
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
import com.github.michaelbull.result.Result
import com.ionspin.kotlin.bignum.integer.toBigInteger
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.yield

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

  context("Concurrent syncs") {
    test("a sync of a different wallet is not silently skipped while another sync is in flight") {
      // Regression test for the W3 upgrade resume stall: a concurrent sync used to be dropped
      // and return Ok(Unit) without touching the wallet, so callers that immediately read the
      // balance (e.g. the sweep fee estimator) saw a stale/empty wallet and concluded
      // "no funds to sweep".

      // Released once the first sync is provably holding the sync lock.
      val firstSyncHoldsLock = CompletableDeferred<Unit>()
      // Released by the test once the second sync has been started.
      val releaseFirstSync = CompletableDeferred<Unit>()
      val slowBlockchainProvider = object : BdkBlockchainProvider {
        private var calls = 0

        override suspend fun blockchain(electrumServer: ElectrumServer?) =
          BdkResult.Ok(BdkBlockchainProviderMock.BdkBlockchainMock)

        override suspend fun legacyBlockchain(
          electrumServer: ElectrumServer?,
        ): BdkResult<BdkBlockchain> {
          // Suspend the *first* sync inside the lock so the second one races it.
          if (calls++ == 0) {
            firstSyncHoldsLock.complete(Unit)
            releaseFirstSync.await()
          }
          return BdkResult.Ok(BdkBlockchainProviderMock.BdkBlockchainMock)
        }

        override suspend fun getBlockchain(
          electrumServer: ElectrumServer,
          timeout: kotlin.time.Duration,
        ) = BdkResult.Ok(
          BdkBlockchainHolder(
            electrumServer = BdkBlockchainProviderMock.ElectrumServerMock,
            bdkBlockchain = BdkBlockchainProviderMock.BdkBlockchainMock
          )
        )
      }

      val syncer = BdkWalletSyncerImpl(
        bdkBlockchainProvider = slowBlockchainProvider,
        clock = ClockFake(),
        datadogRumMonitor = datadogRumMonitor,
        deviceInfoProvider = deviceInfoProvider,
        electrumServerSettingProvider = electrumServerSettingProvider,
        electrumReachability = reachabilityMock,
        networkReachabilityProvider = networkReachabilityProvider
      )

      coroutineScope {
        val walletA = CountingBdkWallet()
        val walletB = CountingBdkWallet()

        val first = async { syncer.sync(walletA, BITCOIN) }
        // Deterministically wait until the first sync is inside the lock.
        firstSyncHoldsLock.await()

        val second = async { syncer.sync(walletB, BITCOIN) }
        // Let the second sync reach the lock check (where it used to bail out with Ok).
        repeat(10) { yield() }

        releaseFirstSync.complete(Unit)
        first.await().shouldBeOk()
        second.await().shouldBeOk()

        // Before the fix, walletB's sync was dropped entirely (syncCount == 0) while still
        // reporting success, which is what made the resumed W3 upgrade see zero funds.
        walletB.syncCount.shouldBe(1)
        walletA.syncCount.shouldBe(1)

        // Drain the RUM/reachability turbines for both syncs so teardown doesn't complain.
        repeat(2) {
          datadogRumMonitor.startResourceLoadingCalls.awaitItem()
          datadogRumMonitor.stopResourceLoadingCalls.awaitItem()
          networkReachabilityProvider.updateNetworkReachabilityForConnectionCalls.awaitItem()
        }
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

/**
 * A [BdkWallet] that records how many times it was actually synced, so tests can distinguish
 * "sync succeeded" from "sync was silently skipped but reported success".
 */
private class CountingBdkWallet : BdkWallet {
  var syncCount = 0
    private set

  override fun syncBlocking(
    blockchain: BdkBlockchain,
    progress: BdkProgress?,
  ): BdkResult<Unit> {
    syncCount++
    return BdkResult.Ok(Unit)
  }

  override fun listTransactionsBlocking(
    includeRaw: Boolean,
  ): BdkResult<List<BdkTransactionDetails>> = BdkResult.Ok(emptyList())

  override fun getBalanceBlocking(): BdkResult<BdkBalance> =
    BdkResult.Ok(
      BdkBalance(
        0u.toBigInteger(),
        0u.toBigInteger(),
        0u.toBigInteger(),
        0u.toBigInteger(),
        0u.toBigInteger(),
        0u.toBigInteger()
      )
    )

  override fun signBlocking(psbt: BdkPartiallySignedTransaction): BdkResult<Boolean> =
    BdkResult.Ok(true)

  override fun getAddressBlocking(addressIndex: BdkAddressIndex): BdkResult<BdkAddressInfo> =
    BdkResult.Ok(BdkAddressInfo(0, BdkAddressMock()))

  override fun isMineBlocking(script: BdkScript): BdkResult<Boolean> = BdkResult.Ok(false)

  override fun listUnspentBlocking(): BdkResult<List<BdkUtxo>> = BdkResult.Ok(emptyList())
}

private fun Result<Unit, BdkError>.shouldBeOk() {
  isOk.shouldBe(true)
}
