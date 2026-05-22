package build.wallet.bitcoin.wallet

import app.cash.turbine.test
import build.wallet.bdk.bindings.BdkError
import build.wallet.bdk.bindings.BdkOutPointMock
import build.wallet.bdk.bindings.BdkScriptMock
import build.wallet.bdk.bindings.BdkUtxoMock
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitcoin.balance.BitcoinBalance
import build.wallet.bitcoin.bdk.BdkTransactionMapperV2
import build.wallet.bitcoin.bdk.BdkWalletSyncerV2Fake
import build.wallet.bitcoin.fees.BitcoinFeeRateEstimatorMock
import build.wallet.bitcoin.fees.Fee
import build.wallet.bitcoin.fees.FeePolicy
import build.wallet.bitcoin.fees.FeeRate
import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount
import build.wallet.bitcoin.transactions.PsbtMock
import build.wallet.bitcoin.wallet.SpendingWallet.PsbtConstructionMethod
import build.wallet.coroutines.turbine.awaitNoEvents
import build.wallet.coroutines.turbine.turbines
import build.wallet.money.BitcoinMoney
import build.wallet.platform.app.AppSessionManagerFake
import build.wallet.testing.shouldBeErrOfType
import build.wallet.testing.shouldBeOk
import com.github.michaelbull.result.Err
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.floats.shouldBeNaN
import io.kotest.matchers.shouldBe
import uniffi.bdk.Amount
import uniffi.bdk.Balance
import uniffi.bdk.BlockHash
import uniffi.bdk.BlockId
import uniffi.bdk.CanonicalTx
import uniffi.bdk.LocalOutput
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

  test("sync publishes after first BDK2 sync") {
    val bdkWallet = InitializationTestWallet(checkpointHeight = 0u)
    walletSyncer.onSync = {
      bdkWallet.checkpointHeight = 1u
    }
    val wallet = buildWallet(bdkWallet)

    wallet.balance().test {
      val balanceTurbine = this
      wallet.transactions().test {
        val transactionsTurbine = this
        wallet.unspentOutputs().test {
          val unspentOutputsTurbine = this

          wallet.sync().shouldBeOk()

          walletSyncer.syncCalls.awaitItem()
          balanceTurbine.awaitItem().shouldBe(BitcoinBalance.ZeroBalance)
          transactionsTurbine.awaitItem().shouldBeEmpty()
          unspentOutputsTurbine.awaitItem().shouldBeEmpty()
        }
      }
    }
  }

  test("Balance and transaction initialization waits before first BDK2 sync") {
    val wallet = buildWallet(InitializationTestWallet(checkpointHeight = 0u))

    wallet.balance().test {
      val balanceTurbine = this
      wallet.transactions().test {
        val transactionsTurbine = this
        wallet.unspentOutputs().test {
          val unspentOutputsTurbine = this

          wallet.initializeBalanceAndTransactions()

          balanceTurbine.awaitNoEvents()
          transactionsTurbine.awaitNoEvents()
          unspentOutputsTurbine.awaitNoEvents()
          walletSyncer.syncCalls.awaitNoEvents()
        }
      }
    }
  }

  test("Balance and transaction initialization publishes cached data after first BDK2 sync") {
    val wallet = buildWallet(InitializationTestWallet(checkpointHeight = 1u))

    wallet.balance().test {
      val balanceTurbine = this
      wallet.transactions().test {
        val transactionsTurbine = this
        wallet.unspentOutputs().test {
          val unspentOutputsTurbine = this

          wallet.initializeBalanceAndTransactions()

          balanceTurbine.awaitItem().shouldBe(BitcoinBalance.ZeroBalance)
          transactionsTurbine.awaitItem().shouldBeEmpty()
          unspentOutputsTurbine.awaitItem().shouldBeEmpty()
          walletSyncer.syncCalls.awaitNoEvents()
        }
      }
    }
  }

  test("Balance and transaction initialization remains best-effort when transaction loading fails") {
    val wallet = buildWallet(
      InitializationTestWallet(
        checkpointHeight = 1u,
        transactionsError = RuntimeException("transactions failed")
      )
    )

    wallet.balance().test {
      val balanceTurbine = this
      wallet.transactions().test {
        val transactionsTurbine = this
        wallet.unspentOutputs().test {
          val unspentOutputsTurbine = this

          wallet.initializeBalanceAndTransactions()

          balanceTurbine.awaitItem().shouldBe(BitcoinBalance.ZeroBalance)
          unspentOutputsTurbine.awaitItem().shouldBeEmpty()
          transactionsTurbine.awaitNoEvents()
          walletSyncer.syncCalls.awaitNoEvents()
        }
      }
    }
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

  test("createSignedPsbt returns InvalidFeeRate for FeeBumpWithDrain with zero fee rate") {
    val wallet = buildWallet()
    val result = wallet.createSignedPsbt(
      constructionType = PsbtConstructionMethod.FeeBumpWithDrain(
        txid = "abc123",
        feeRate = FeeRate(0f),
        drainToScript = BdkScriptMock()
      )
    )

    result.shouldBeErrOfType<SpendingWalletV2Error.InvalidFeeRate>()
      .satsPerVByte.shouldBe(0f)
  }

  test("createSignedPsbt returns InvalidFeeRate for FeeBumpWithDrain with negative fee rate") {
    val wallet = buildWallet()
    val result = wallet.createSignedPsbt(
      constructionType = PsbtConstructionMethod.FeeBumpWithDrain(
        txid = "abc123",
        feeRate = FeeRate(-1f),
        drainToScript = BdkScriptMock()
      )
    )

    result.shouldBeErrOfType<SpendingWalletV2Error.InvalidFeeRate>()
      .satsPerVByte.shouldBe(-1f)
  }

  test("createSignedPsbt returns InvalidFeeRate for FeeBumpWithDrain with NaN fee rate") {
    val wallet = buildWallet()
    val result = wallet.createSignedPsbt(
      constructionType = PsbtConstructionMethod.FeeBumpWithDrain(
        txid = "abc123",
        feeRate = FeeRate(Float.NaN),
        drainToScript = BdkScriptMock()
      )
    )

    result.shouldBeErrOfType<SpendingWalletV2Error.InvalidFeeRate>()
      .satsPerVByte.shouldBeNaN()
  }

  test("createSignedPsbt returns InvalidFeeRate for FeeBumpWithDrain with infinite fee rate") {
    val wallet = buildWallet()
    val result = wallet.createSignedPsbt(
      constructionType = PsbtConstructionMethod.FeeBumpWithDrain(
        txid = "abc123",
        feeRate = FeeRate(Float.POSITIVE_INFINITY),
        drainToScript = BdkScriptMock()
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
    val bdkWallet = InitializationTestWallet(checkpointHeight = 0u)
    walletSyncer.syncResult = Err(syncFailure)
    walletSyncer.onSync = {
      bdkWallet.checkpointHeight = 1u
    }
    val wallet = buildWallet(bdkWallet)
    val result = wallet.sync()

    walletSyncer.syncCalls.awaitItem()
    bdkWallet.checkpointHeight.shouldBe(0u)
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

  test("createSignedPsbt returns InvalidFeeRate for Cpfp with zero fee rate") {
    val wallet = buildWallet()
    val result = wallet.createSignedPsbt(
      constructionType = PsbtConstructionMethod.Cpfp(
        utxoOutpoint = BdkOutPointMock,
        recipientAddress = testAddress,
        feePolicy = FeePolicy.Rate(FeeRate(0f))
      )
    )

    result.shouldBeErrOfType<SpendingWalletV2Error.InvalidFeeRate>()
      .satsPerVByte.shouldBe(0f)
  }

  test("createSignedPsbt returns InvalidFeeRate for Cpfp with negative fee rate") {
    val wallet = buildWallet()
    val result = wallet.createSignedPsbt(
      constructionType = PsbtConstructionMethod.Cpfp(
        utxoOutpoint = BdkOutPointMock,
        recipientAddress = testAddress,
        feePolicy = FeePolicy.Rate(FeeRate(-1f))
      )
    )

    result.shouldBeErrOfType<SpendingWalletV2Error.InvalidFeeRate>()
      .satsPerVByte.shouldBe(-1f)
  }

  test("createSignedPsbt returns InvalidFeeRate for Cpfp with NaN fee rate") {
    val wallet = buildWallet()
    val result = wallet.createSignedPsbt(
      constructionType = PsbtConstructionMethod.Cpfp(
        utxoOutpoint = BdkOutPointMock,
        recipientAddress = testAddress,
        feePolicy = FeePolicy.Rate(FeeRate(Float.NaN))
      )
    )

    result.shouldBeErrOfType<SpendingWalletV2Error.InvalidFeeRate>()
      .satsPerVByte.shouldBeNaN()
  }

  test("createSignedPsbt returns InvalidFeeRate for Cpfp with infinite fee rate") {
    val wallet = buildWallet()
    val result = wallet.createSignedPsbt(
      constructionType = PsbtConstructionMethod.Cpfp(
        utxoOutpoint = BdkOutPointMock,
        recipientAddress = testAddress,
        feePolicy = FeePolicy.Rate(FeeRate(Float.POSITIVE_INFINITY))
      )
    )

    result.shouldBeErrOfType<SpendingWalletV2Error.InvalidFeeRate>()
      .satsPerVByte.shouldBe(Float.POSITIVE_INFINITY)
  }

  test("createSignedPsbt for Cpfp with MinRelayRate passes fee validation and attempts BDK build") {
    val wallet = buildWallet()
    val result = wallet.createSignedPsbt(
      constructionType = PsbtConstructionMethod.Cpfp(
        utxoOutpoint = BdkOutPointMock,
        recipientAddress = testAddress,
        feePolicy = FeePolicy.MinRelayRate
      )
    )

    // NoPointer wallet causes BDK to fail, but it should not be an InvalidFeeRate error
    result.shouldBeErrOfType<BdkError>()
  }

  test("createSignedPsbt for Cpfp with Absolute fee passes fee validation and attempts BDK build") {
    val wallet = buildWallet()
    val result = wallet.createSignedPsbt(
      constructionType = PsbtConstructionMethod.Cpfp(
        utxoOutpoint = BdkOutPointMock,
        recipientAddress = testAddress,
        feePolicy = FeePolicy.Absolute(
          fee = Fee(amount = BitcoinMoney.sats(500))
        )
      )
    )

    // NoPointer wallet causes BDK to fail, but it should not be an InvalidFeeRate error
    result.shouldBeErrOfType<BdkError>()
  }
})

private class InitializationTestWallet(
  var checkpointHeight: UInt,
  val transactionsError: Throwable? = null,
) : BdkV2Wallet(NoPointer) {
  override fun latestCheckpoint(): BlockId =
    BlockId(
      height = checkpointHeight,
      hash = BlockHash(NoPointer)
    )

  override fun balance(): Balance =
    Balance(
      immature = Amount.fromSat(0uL),
      trustedPending = Amount.fromSat(0uL),
      untrustedPending = Amount.fromSat(0uL),
      confirmed = Amount.fromSat(0uL),
      trustedSpendable = Amount.fromSat(0uL),
      total = Amount.fromSat(0uL)
    )

  override fun transactions(): List<CanonicalTx> {
    transactionsError?.let { throw it }
    return emptyList()
  }

  override fun listUnspent(): List<LocalOutput> = emptyList()
}
