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
import build.wallet.feature.flags.AugurFeeComparisonLoggingFeatureFlag
import build.wallet.feature.flags.AugurFeesEstimationFeatureFlag
import com.github.michaelbull.result.getOrThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class BitcoinFeeRateEstimatorImplTests : FunSpec({
  val bdkBlockchainMock = BdkBlockchainMock(
    blockHeightResult = BdkResult.Ok(1),
    blockHashResult = BdkResult.Ok(BitcoinNetworkType.BITCOIN.chainHash()),
    broadcastResult = BdkResult.Ok("txid"),
    feeRateResult = BdkResult.Ok(22f),
    getTxResult = BdkResult.Ok(BdkTransactionMock(output = listOf(BdkTxOutMock)))
  )

  val bdkBlockchainProvider = BdkBlockchainProviderMock(
    turbines::create,
    blockchainResult = BdkResult.Ok(bdkBlockchainMock)
  )

  val featureFlagDao = FeatureFlagDaoFake()
  val augurFeesEstimationFeatureFlag = AugurFeesEstimationFeatureFlag(featureFlagDao)
  val augurFeeComparisonLoggingFeatureFlag = AugurFeeComparisonLoggingFeatureFlag(featureFlagDao)
  val mempoolHttpClientMock = MempoolHttpClientMock()
  val augurFeesHttpClientMock = AugurFeesHttpClientMock()

  val feeRateEstimator = BitcoinFeeRateEstimatorImpl(
    mempoolHttpClient = mempoolHttpClientMock,
    augurFeesHttpClient = augurFeesHttpClientMock,
    bdkBlockchainProvider = bdkBlockchainProvider,
    augurFeesEstimationFeatureFlag = augurFeesEstimationFeatureFlag,
    augurFeeComparisonLoggingFeatureFlag = augurFeeComparisonLoggingFeatureFlag
  )

  beforeTest {
    featureFlagDao.reset()
    augurFeesEstimationFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
    augurFeeComparisonLoggingFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(false))
    bdkBlockchainProvider.reset()
    bdkBlockchainProvider.blockchainResult = BdkResult.Ok(bdkBlockchainMock)
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

  test("Should return Augur fees when comparison logging enabled and Augur flag enabled") {
    // Enable both comparison logging and Augur fees
    augurFeeComparisonLoggingFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
    augurFeesEstimationFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))

    val feeRate = feeRateEstimator.estimatedFeeRateForTransaction(
      networkType = BitcoinNetworkType.BITCOIN,
      estimatedTransactionPriority = FASTEST
    )

    // Should return Augur fee rate (three_blocks @ 95% confidence = 10 sats/vB)
    feeRate.satsPerVByte.shouldBe(10f)
  }

  test("Should return mempool fees when comparison logging enabled but Augur flag disabled") {
    // Enable comparison logging but disable Augur fees
    augurFeeComparisonLoggingFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
    augurFeesEstimationFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(false))

    val feeRate = feeRateEstimator.estimatedFeeRateForTransaction(
      networkType = BitcoinNetworkType.BITCOIN,
      estimatedTransactionPriority = FASTEST
    )

    // Should return mempool fee rate (fastestFee = 5.0 sats/vB)
    feeRate.satsPerVByte.shouldBe(5.0f)
  }

  test("Should return Augur rate when mempool fails and comparison logging enabled") {
    augurFeeComparisonLoggingFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
    mempoolHttpClientMock.error = Error("mempool error")

    val feeRate = feeRateEstimator.estimatedFeeRateForTransaction(
      networkType = BitcoinNetworkType.BITCOIN,
      estimatedTransactionPriority = FASTEST
    )

    // Should still return Augur fee rate (comparison logging failure doesn't affect result)
    feeRate.satsPerVByte.shouldBe(10f)
  }

  test("Should fallback to BDK when both sources fail and comparison logging enabled") {
    augurFeeComparisonLoggingFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
    augurFeesHttpClientMock.error = Error("augur error")
    mempoolHttpClientMock.error = Error("mempool error")

    val feeRate = feeRateEstimator.estimatedFeeRateForTransaction(
      networkType = BitcoinNetworkType.BITCOIN,
      estimatedTransactionPriority = FASTEST
    )

    // Should fallback to BDK (comparison logging fails silently)
    feeRate.satsPerVByte.shouldBe(22f)
    bdkBlockchainProvider.blockchainCalls.awaitItem()
  }

  test("Should return Augur fee rates when comparison logging enabled and Augur flag enabled") {
    // Enable both comparison logging and Augur fees
    augurFeeComparisonLoggingFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
    augurFeesEstimationFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))

    val feeRates = feeRateEstimator.getEstimatedFeeRates(BitcoinNetworkType.BITCOIN).getOrThrow()

    // Should return Augur fee rates
    feeRates.fastestFeeRate.satsPerVByte.shouldBe(10f)
    feeRates.halfHourFeeRate.satsPerVByte.shouldBe(5f)
    feeRates.hourFeeRate.satsPerVByte.shouldBe(3f)
  }

  test("Should return mempool fee rates when comparison logging enabled but Augur flag disabled") {
    // Enable comparison logging but disable Augur fees
    augurFeeComparisonLoggingFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
    augurFeesEstimationFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(false))

    val feeRates = feeRateEstimator.getEstimatedFeeRates(BitcoinNetworkType.BITCOIN).getOrThrow()

    // Should return mempool fee rates
    feeRates.fastestFeeRate.satsPerVByte.shouldBe(5.0f)
    feeRates.halfHourFeeRate.satsPerVByte.shouldBe(3.0f)
    feeRates.hourFeeRate.satsPerVByte.shouldBe(2.0f)
  }

  test("Should return Augur fee rates when mempool fails and comparison logging enabled") {
    augurFeeComparisonLoggingFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
    mempoolHttpClientMock.error = Error("mempool error")

    val feeRates = feeRateEstimator.getEstimatedFeeRates(BitcoinNetworkType.BITCOIN).getOrThrow()

    // Should still return Augur fee rates (comparison logging failure doesn't affect result)
    feeRates.fastestFeeRate.satsPerVByte.shouldBe(10f)
    feeRates.halfHourFeeRate.satsPerVByte.shouldBe(5f)
    feeRates.hourFeeRate.satsPerVByte.shouldBe(3f)
  }

  test("Should fallback to BDK for fee rates when both sources fail and comparison logging enabled") {
    augurFeeComparisonLoggingFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
    augurFeesHttpClientMock.error = Error("augur error")
    mempoolHttpClientMock.error = Error("mempool error")

    val feeRates = feeRateEstimator.getEstimatedFeeRates(BitcoinNetworkType.BITCOIN).getOrThrow()

    // Should fallback to BDK (comparison logging fails silently)
    feeRates.fastestFeeRate.satsPerVByte.shouldBe(22f)
    feeRates.halfHourFeeRate.satsPerVByte.shouldBe(22f)
    feeRates.hourFeeRate.satsPerVByte.shouldBe(22f)
    bdkBlockchainProvider.blockchainCalls.awaitItem()
  }
})
