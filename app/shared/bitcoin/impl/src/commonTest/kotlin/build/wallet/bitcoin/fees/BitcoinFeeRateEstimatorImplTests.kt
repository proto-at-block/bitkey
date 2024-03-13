package build.wallet.bitcoin.fees

import build.wallet.bdk.bindings.BdkBlockchainMock
import build.wallet.bdk.bindings.BdkResult
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.bdk.BdkBlockchainProviderMock
import build.wallet.bitcoin.sync.chainHash
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority
import build.wallet.coroutines.turbine.turbines
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.utils.io.errors.IOException

class BitcoinFeeRateEstimatorImplTests : FunSpec({
  val bdkBlockchain = BdkBlockchainMock(
    blockHeightResult = BdkResult.Ok(1),
    blockHashResult = BdkResult.Ok(build.wallet.bitcoin.BitcoinNetworkType.BITCOIN.chainHash()),
    broadcastResult = BdkResult.Ok(Unit),
    feeRateResult = BdkResult.Ok(22f)
  )
  val bdkBlockchainProvider = BdkBlockchainProviderMock(
    turbines::create,
    blockchainResult = BdkResult.Ok(bdkBlockchain)
  )
  val failingMempoolHttpClient = HttpClient(MockEngine) {
    engine {
      // Always respond with an error
      addHandler { _ ->
        throw IOException("Error")
      }
    }
  }

  context("mempool.space is unreachable") {
    val feeRateEstimator = BitcoinFeeRateEstimatorImpl(
      mempoolHttpClient = MempoolHttpClientMock(
        httpClient = failingMempoolHttpClient
      ),
      bdkBlockchainProvider = bdkBlockchainProvider
    )

    test("Should return result from bdkBlockchain (source by Electrum)") {
      feeRateEstimator.estimatedFeeRateForTransaction(
        networkType = BitcoinNetworkType.BITCOIN,
        estimatedTransactionPriority = EstimatedTransactionPriority.FASTEST
      ).satsPerVByte.shouldBe(22f)

      // Should have asked blockchain for information.
      bdkBlockchainProvider.blockchainCalls.awaitItem()
    }
  }
})
