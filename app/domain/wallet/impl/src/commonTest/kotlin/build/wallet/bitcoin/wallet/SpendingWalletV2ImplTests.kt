package build.wallet.bitcoin.wallet

import build.wallet.bdk.bindings.BdkError
import build.wallet.bdk.bindings.BdkUtxoMock
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitcoin.bdk.BdkTransactionMapperV2
import build.wallet.bitcoin.bdk.BdkWalletSyncerV2Fake
import build.wallet.bitcoin.fees.BitcoinFeeRateEstimatorMock
import build.wallet.bitcoin.fees.FeePolicy
import build.wallet.bitcoin.fees.FeeRate
import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount
import build.wallet.bitcoin.transactions.PsbtMock
import build.wallet.bitcoin.wallet.SpendingWallet.PsbtConstructionMethod
import build.wallet.coroutines.turbine.turbines
import build.wallet.money.BitcoinMoney
import build.wallet.platform.app.AppSessionManagerFake
import build.wallet.testing.shouldBeErrOfType
import com.github.michaelbull.result.Err
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.floats.shouldBeNaN
import io.kotest.matchers.shouldBe
import uniffi.bdk.NoPointer
import uniffi.bdk.Persister
import uniffi.bdk.Wallet as BdkV2Wallet

class SpendingWalletV2ImplTests : FunSpec({
  val appSessionManager = AppSessionManagerFake()
  val bitcoinFeeRateEstimator = BitcoinFeeRateEstimatorMock()

  val testAddress = BitcoinAddress("tb1qtest")
  val testFeeRate = FeeRate(1f)
  val testFeePolicy = FeePolicy.Rate(testFeeRate)
  val testAmount = BitcoinTransactionSendAmount.ExactAmount(BitcoinMoney.sats(1000))
  val syncFailure = BdkError.Generic(RuntimeException("sync failed"), "sync failed")

  val mapper = object : BdkTransactionMapperV2 {
    override suspend fun createTransaction(
      txDetails: uniffi.bdk.TxDetails,
      wallet: BdkV2Wallet,
      networkType: BitcoinNetworkType,
    ) = error("Not used in these tests")

    override fun createUtxo(localOutput: uniffi.bdk.LocalOutput) = error("Not used in these tests")
  }

  val walletSyncer = BdkWalletSyncerV2Fake(turbines::create)

  fun buildWallet(bdkWallet: BdkV2Wallet = BdkV2Wallet(NoPointer)) =
    SpendingWalletV2Impl(
      identifier = "test-wallet",
      networkType = BitcoinNetworkType.SIGNET,
      bdkWallet = bdkWallet,
      persister = Persister(NoPointer),
      appSessionManager = appSessionManager,
      bdkTransactionMapperV2 = mapper,
      bdkWalletSyncerV2 = walletSyncer,
      bitcoinFeeRateEstimator = bitcoinFeeRateEstimator
    )

  beforeTest {
    appSessionManager.reset()
    bitcoinFeeRateEstimator.reset()
    walletSyncer.reset()
  }

  test("createSignedPsbt returns InvalidFeeRate for FeeBump with zero fee rate") {
    val wallet = buildWallet()
    val result = wallet.createSignedPsbt(
      constructionType = PsbtConstructionMethod.FeeBump(
        txid = "abc123",
        feeRate = FeeRate(0f)
      )
    )

    result.shouldBeErrOfType<SpendingWalletV2Error.InvalidFeeRate>()
      .satsPerVByte.shouldBe(0f)
  }

  test("createSignedPsbt returns InvalidFeeRate for FeeBump with negative fee rate") {
    val wallet = buildWallet()
    val result = wallet.createSignedPsbt(
      constructionType = PsbtConstructionMethod.FeeBump(
        txid = "abc123",
        feeRate = FeeRate(-1f)
      )
    )

    result.shouldBeErrOfType<SpendingWalletV2Error.InvalidFeeRate>()
      .satsPerVByte.shouldBe(-1f)
  }

  test("createSignedPsbt returns InvalidFeeRate for FeeBump with NaN fee rate") {
    val wallet = buildWallet()
    val result = wallet.createSignedPsbt(
      constructionType = PsbtConstructionMethod.FeeBump(
        txid = "abc123",
        feeRate = FeeRate(Float.NaN)
      )
    )

    result.shouldBeErrOfType<SpendingWalletV2Error.InvalidFeeRate>()
      .satsPerVByte.shouldBeNaN()
  }

  test("createSignedPsbt returns InvalidFeeRate for FeeBump with infinite fee rate") {
    val wallet = buildWallet()
    val result = wallet.createSignedPsbt(
      constructionType = PsbtConstructionMethod.FeeBump(
        txid = "abc123",
        feeRate = FeeRate(Float.POSITIVE_INFINITY)
      )
    )

    result.shouldBeErrOfType<SpendingWalletV2Error.InvalidFeeRate>()
      .satsPerVByte.shouldBe(Float.POSITIVE_INFINITY)
  }

  test("createSignedPsbt returns InvalidFeeRate for Regular with invalid fee rate") {
    val wallet = buildWallet()
    val result = wallet.createSignedPsbt(
      constructionType = PsbtConstructionMethod.Regular(
        recipientAddress = testAddress,
        amount = testAmount,
        feePolicy = FeePolicy.Rate(FeeRate(0f))
      )
    )

    result.shouldBeErrOfType<SpendingWalletV2Error.InvalidFeeRate>()
  }

  test("createSignedPsbt returns InvalidFeeRate for DrainAllFromUtxos with invalid fee rate") {
    val wallet = buildWallet()
    val result = wallet.createSignedPsbt(
      constructionType = PsbtConstructionMethod.DrainAllFromUtxos(
        recipientAddress = testAddress,
        utxos = setOf(BdkUtxoMock),
        feePolicy = FeePolicy.Rate(FeeRate(0f))
      )
    )

    result.shouldBeErrOfType<SpendingWalletV2Error.InvalidFeeRate>()
  }

  test("sync returns SyncFailed when syncer fails") {
    walletSyncer.syncResult = Err(syncFailure)
    val wallet = buildWallet()
    val result = wallet.sync()

    walletSyncer.syncCalls.awaitItem()
    result.shouldBeErrOfType<SpendingWalletV2Error.SyncFailed>()
      .cause
      .shouldBe(syncFailure)
  }

  test("createPsbt returns InvalidFeeRate for zero fee rate") {
    val wallet = buildWallet()
    val result = wallet.createPsbt(
      recipientAddress = testAddress,
      amount = testAmount,
      feePolicy = FeePolicy.Rate(FeeRate(0f)),
      coinSelectionStrategy = CoinSelectionStrategy.Default
    )

    result.shouldBeErrOfType<SpendingWalletV2Error.InvalidFeeRate>()
      .satsPerVByte.shouldBe(0f)
  }

  test("createPsbt returns InvalidFeeRate for negative fee rate") {
    val wallet = buildWallet()
    val result = wallet.createPsbt(
      recipientAddress = testAddress,
      amount = testAmount,
      feePolicy = FeePolicy.Rate(FeeRate(-5f)),
      coinSelectionStrategy = CoinSelectionStrategy.Default
    )

    result.shouldBeErrOfType<SpendingWalletV2Error.InvalidFeeRate>()
      .satsPerVByte.shouldBe(-5f)
  }

  test("createPsbt returns InvalidFeeRate for NaN fee rate") {
    val wallet = buildWallet()
    val result = wallet.createPsbt(
      recipientAddress = testAddress,
      amount = testAmount,
      feePolicy = FeePolicy.Rate(FeeRate(Float.NaN)),
      coinSelectionStrategy = CoinSelectionStrategy.Default
    )

    result.shouldBeErrOfType<SpendingWalletV2Error.InvalidFeeRate>()
      .satsPerVByte.shouldBeNaN()
  }

  test("createPsbt returns InvalidFeeRate for infinite fee rate") {
    val wallet = buildWallet()
    val result = wallet.createPsbt(
      recipientAddress = testAddress,
      amount = testAmount,
      feePolicy = FeePolicy.Rate(FeeRate(Float.POSITIVE_INFINITY)),
      coinSelectionStrategy = CoinSelectionStrategy.Default
    )

    result.shouldBeErrOfType<SpendingWalletV2Error.InvalidFeeRate>()
      .satsPerVByte.shouldBe(Float.POSITIVE_INFINITY)
  }

  test("createPsbt returns error when TxBuilder fails") {
    val wallet = buildWallet()
    val result = wallet.createPsbt(
      recipientAddress = testAddress,
      amount = testAmount,
      feePolicy = testFeePolicy,
      coinSelectionStrategy = CoinSelectionStrategy.Default
    )

    // With NoPointer wallet, BDK operations will fail
    result.shouldBeErrOfType<BdkError>()
  }

  test("signPsbt returns PsbtSigningFailed when PSBT parsing fails") {
    val wallet = buildWallet()
    val invalidPsbt = PsbtMock.copy(base64 = "invalid-base64")
    val result = wallet.signPsbt(invalidPsbt)

    result.shouldBeErrOfType<SpendingWalletV2Error.PsbtSigningFailed>()
  }
})
