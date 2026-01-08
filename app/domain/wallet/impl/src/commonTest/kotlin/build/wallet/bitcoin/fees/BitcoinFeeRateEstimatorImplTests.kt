package build.wallet.bitcoin.fees

import build.wallet.bdk.bindings.BdkBlockchainMock
import build.wallet.bdk.bindings.BdkError
import build.wallet.bdk.bindings.BdkResult
import build.wallet.bdk.bindings.BdkTransactionMock
import build.wallet.bdk.bindings.BdkTxOutMock
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.bdk.BdkBlockchainProviderMock
import build.wallet.bitcoin.sync.chainHash
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority.*
import build.wallet.coroutines.turbine.turbines
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.FeatureFlagValue
import build.wallet.feature.flags.AugurFeesEstimationFeatureFlag
import com.github.michaelbull.result.getOrThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class BitcoinFeeRateEstimatorImplTests : FunSpec({
  val bdkBlockchain = BdkBlockchainMock(
    blockHeightResult = BdkResult.Ok(1),
    blockHashResult = BdkResult.Ok(BitcoinNetworkType.BITCOIN.chainHash()),
    broadcastResult = BdkResult.Ok(Unit),
    feeRateResult = BdkResult.Ok(22f),
    getTxResult = BdkResult.Ok(BdkTransactionMock(output = listOf(BdkTxOutMock)))
  )

  val bdkBlockchainProvider = BdkBlockchainProviderMock(
    turbines::create,
    blockchainResult = BdkResult.Ok(bdkBlockchain)
  )

  val featureFlagDao = FeatureFlagDaoFake()
  val augurFeesEstimationFeatureFlag = AugurFeesEstimationFeatureFlag(featureFlagDao)
  val mempoolHttpClientMock = MempoolHttpClientMock()
  val augurFeesHttpClientMock = AugurFeesHttpClientMock()

  val feeRateEstimator = BitcoinFeeRateEstimatorImpl(
    mempoolHttpClient = mempoolHttpClientMock,
    augurFeesHttpClient = augurFeesHttpClientMock,
    bdkBlockchainProvider = bdkBlockchainProvider,
    augurFeesEstimationFeatureFlag = augurFeesEstimationFeatureFlag
  )

  beforeTest {
    featureFlagDao.reset()
    augurFeesEstimationFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
    bdkBlockchainProvider.reset()
    bdkBlockchainProvider.blockchainResult = BdkResult.Ok(bdkBlockchain)
    mempoolHttpClientMock.reset()
    augurFeesHttpClientMock.reset()
  }

  test("Should use AugurFees for FASTEST priority") {
    val feeRate = feeRateEstimator.estimatedFeeRateForTransaction(
      networkType = BitcoinNetworkType.BITCOIN,
      estimatedTransactionPriority = FASTEST
    )

    // FASTEST should use three_blocks @95% confidence = 10 sats/vB
    feeRate.satsPerVByte.shouldBe(10f)
  }

  test("Should use AugurFees for THIRTY_MINUTES priority") {
    val feeRate = feeRateEstimator.estimatedFeeRateForTransaction(
      networkType = BitcoinNetworkType.BITCOIN,
      estimatedTransactionPriority = THIRTY_MINUTES
    )

    // THIRTY_MINUTES should use three_blocks @80% confidence = 5 sats/vB
    feeRate.satsPerVByte.shouldBe(5f)
  }

  test("Should use AugurFees for SIXTY_MINUTES priority") {
    val feeRate = feeRateEstimator.estimatedFeeRateForTransaction(
      networkType = BitcoinNetworkType.BITCOIN,
      estimatedTransactionPriority = SIXTY_MINUTES
    )

    // SIXTY_MINUTES should use six_blocks @80% confidence = 3 sats/vB
    feeRate.satsPerVByte.shouldBe(3f)
  }

  test("Should fallback to mempool when AugurFees fails") {
    augurFeesHttpClientMock.error = Error("oh no")

    val feeRate = feeRateEstimator.estimatedFeeRateForTransaction(
      networkType = BitcoinNetworkType.BITCOIN,
      estimatedTransactionPriority = FASTEST
    )

    // Should fallback to mempool fastest fee
    feeRate.satsPerVByte.shouldBe(5.0f)
  }

  test("Should get all fee rates when AugurFees succeeds") {
    val feeRates = feeRateEstimator.getEstimatedFeeRates(BitcoinNetworkType.BITCOIN).getOrThrow()

    // FASTEST: three_blocks @ 95% confidence
    feeRates.fastestFeeRate.satsPerVByte.shouldBe(10f)
    // THIRTY_MINUTES: three_blocks @ 80% confidence
    feeRates.halfHourFeeRate.satsPerVByte.shouldBe(5f)
    // SIXTY_MINUTES: six_blocks @ 80% confidence
    feeRates.hourFeeRate.satsPerVByte.shouldBe(3f)
  }

  test("Should fallback to mempool for all fee rates when AugurFees fails") {
    augurFeesHttpClientMock.error = Error("oh no")

    val feeRates = feeRateEstimator.getEstimatedFeeRates(BitcoinNetworkType.BITCOIN).getOrThrow()

    // Should use mempool values
    feeRates.fastestFeeRate.satsPerVByte.shouldBe(5.0f)
    feeRates.halfHourFeeRate.satsPerVByte.shouldBe(3.0f)
    feeRates.hourFeeRate.satsPerVByte.shouldBe(2.0f)
  }

  test("Should use mempool when AugurFees is disabled") {
    augurFeesEstimationFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(false))

    // Test FASTEST
    val fastestFeeRate = feeRateEstimator.estimatedFeeRateForTransaction(
      networkType = BitcoinNetworkType.BITCOIN,
      estimatedTransactionPriority = FASTEST
    )
    fastestFeeRate.satsPerVByte.shouldBe(5.0f)

    // Test THIRTY_MINUTES
    val thirtyMinFeeRate = feeRateEstimator.estimatedFeeRateForTransaction(
      networkType = BitcoinNetworkType.BITCOIN,
      estimatedTransactionPriority = THIRTY_MINUTES
    )
    thirtyMinFeeRate.satsPerVByte.shouldBe(3.0f)

    // Test SIXTY_MINUTES
    val sixtyMinFeeRate = feeRateEstimator.estimatedFeeRateForTransaction(
      networkType = BitcoinNetworkType.BITCOIN,
      estimatedTransactionPriority = SIXTY_MINUTES
    )
    sixtyMinFeeRate.satsPerVByte.shouldBe(2.0f)
  }

  test("Should get all fee rates from mempool when AugurFees is disabled") {
    augurFeesEstimationFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(false))
    val feeRates = feeRateEstimator.getEstimatedFeeRates(BitcoinNetworkType.BITCOIN).getOrThrow()

    feeRates.fastestFeeRate.satsPerVByte.shouldBe(5.0f)
    feeRates.halfHourFeeRate.satsPerVByte.shouldBe(3.0f)
    feeRates.hourFeeRate.satsPerVByte.shouldBe(2.0f)
  }

  test("Should fallback to BDK when both AugurFees and mempool fail") {
    mempoolHttpClientMock.error = Error("oh no")
    augurFeesHttpClientMock.error = Error("oh no")

    val feeRate = feeRateEstimator.estimatedFeeRateForTransaction(
      networkType = BitcoinNetworkType.BITCOIN,
      estimatedTransactionPriority = FASTEST
    )

    // Should fallback to BDK blockchain estimation
    feeRate.satsPerVByte.shouldBe(22f)
    bdkBlockchainProvider.blockchainCalls.awaitItem()
  }

  test("Should handle BDK blockchain provider failure") {
    bdkBlockchainProvider.blockchainResult =
      BdkResult.Err(BdkError.Generic(Throwable("BDK Error"), null))
    mempoolHttpClientMock.error = Error("oh no")
    augurFeesHttpClientMock.error = Error("oh no")

    val feeRate = feeRateEstimator.estimatedFeeRateForTransaction(
      networkType = BitcoinNetworkType.BITCOIN,
      estimatedTransactionPriority = FASTEST
    )

    // Should use fallback rate when all sources fail
    feeRate.satsPerVByte.shouldBe(1.0f)
    bdkBlockchainProvider.blockchainCalls.awaitItem()
  }
})
