package build.wallet.bitcoin.transactions

import build.wallet.bdk.bindings.BdkError
import build.wallet.bdk.bindings.BdkOutPoint
import build.wallet.bdk.bindings.BdkScriptMock
import build.wallet.bdk.bindings.BdkTxIn
import build.wallet.bdk.bindings.BdkTxOutMock
import build.wallet.bdk.bindings.BdkUtxoMock
import build.wallet.bdk.bindings.BdkUtxoMock2
import build.wallet.bitcoin.BitcoinNetworkType.BITCOIN
import build.wallet.bitcoin.BitcoinNetworkType.SIGNET
import build.wallet.bitcoin.fees.BitcoinFeeRateEstimatorMock
import build.wallet.bitcoin.fees.Fee
import build.wallet.bitcoin.fees.FeeRate
import build.wallet.bitcoin.utxo.Utxos
import build.wallet.bitcoin.wallet.SpendingWallet
import build.wallet.bitcoin.wallet.SpendingWalletMock
import build.wallet.bitcoin.wallet.SpendingWalletV2Error
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.keybox.FullAccountConfigMock
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.compose.collections.immutableListOf
import build.wallet.coroutines.turbine.turbines
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.flags.Bdk2FeatureFlag
import build.wallet.feature.flags.setBdk2Enabled
import build.wallet.money.BitcoinMoney
import build.wallet.testing.shouldBeErrOfType
import build.wallet.testing.shouldBeOk
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class SpeedUpTransactionServiceImplTests : FunSpec({
  val feeRateEstimator = BitcoinFeeRateEstimatorMock()
  val bitcoinWalletService = BitcoinWalletServiceFake()
  val spendingWallet = SpendingWalletMock(turbines::create)
  val feeBumpAllowShrinkingChecker = FeeBumpAllowShrinkingCheckerFake()
  val featureFlagDao = FeatureFlagDaoFake()
  val bdk2FeatureFlag = Bdk2FeatureFlag(featureFlagDao)

  val service = SpeedUpTransactionServiceImpl(
    feeRateEstimator = feeRateEstimator,
    bitcoinWalletService = bitcoinWalletService,
    feeBumpAllowShrinkingChecker = feeBumpAllowShrinkingChecker,
    bdk2FeatureFlag = bdk2FeatureFlag
  )

  beforeTest {
    feeRateEstimator.estimatedFeeRateResult = FeeRate(10.0f)
    bitcoinWalletService.spendingWallet.value = spendingWallet
    bitcoinWalletService.setTransactions(emptyList())
    spendingWallet.reset()
    spendingWallet.createSignedPsbtResult = Ok(PsbtMock)
    feeBumpAllowShrinkingChecker.reset()
    bdk2FeatureFlag.reset()
    bdk2FeatureFlag.initializeFromDao()
  }

  test("returns TransactionNotReplaceable when transaction does not signal RBF") {
    val transaction = BitcoinTransactionMock(
      txid = "no-rbf-tx",
      total = BitcoinMoney.sats(100_000),
      fee = BitcoinMoney.sats(1000),
      confirmationTime = null,
      inputs = immutableListOf(
        BdkTxIn(outpoint = BdkOutPoint("abc", 0u), sequence = UInt.MAX_VALUE, witness = emptyList())
      )
    )

    val result = service.prepareTransactionSpeedUp(FullAccountMock, transaction)

    result.shouldBeErrOfType<SpeedUpTransactionError.TransactionNotReplaceable>()
  }

  test("returns success when transaction signals RBF with sequence below threshold") {
    val transaction = BitcoinTransactionMock(
      txid = "rbf-tx",
      total = BitcoinMoney.sats(100_000),
      fee = BitcoinMoney.sats(1000),
      confirmationTime = null,
      inputs = immutableListOf(
        BdkTxIn(outpoint = BdkOutPoint("abc", 0u), sequence = UInt.MAX_VALUE - 2u, witness = emptyList())
      )
    )

    val result = service.prepareTransactionSpeedUp(FullAccountMock, transaction)

    result.shouldBeOk().apply {
      psbt.shouldBe(PsbtMock)
      details.txid.shouldBe("rbf-tx")
      details.sendAmount.shouldBe(BitcoinMoney.sats(99_000)) // total - fee
      details.oldFee.amount.shouldBe(BitcoinMoney.sats(1000))
    }
  }

  test("returns FailedToPrepareData when transaction fee is null") {
    val transaction = BitcoinTransactionMock(
      txid = "no-fee-tx",
      total = BitcoinMoney.sats(100_000),
      fee = null,
      confirmationTime = null,
      inputs = immutableListOf(
        BdkTxIn(outpoint = BdkOutPoint("abc", 0u), sequence = 0u, witness = emptyList())
      )
    )

    val result = service.prepareTransactionSpeedUp(FullAccountMock, transaction)

    result.shouldBeErrOfType<SpeedUpTransactionError.FailedToPrepareData>()
  }

  test("returns FailedToPrepareData when transaction vsize is null") {
    val transaction = BitcoinTransactionMock(
      txid = "no-vsize-tx",
      total = BitcoinMoney.sats(100_000),
      fee = BitcoinMoney.sats(1000),
      confirmationTime = null,
      inputs = immutableListOf(
        BdkTxIn(outpoint = BdkOutPoint("abc", 0u), sequence = 0u, witness = emptyList())
      )
    ).copy(vsize = null)

    val result = service.prepareTransactionSpeedUp(FullAccountMock, transaction)

    result.shouldBeErrOfType<SpeedUpTransactionError.FailedToPrepareData>()
  }

  test("returns FailedToPrepareData when transaction vsize is zero") {
    val transaction = BitcoinTransactionMock(
      txid = "zero-vsize-tx",
      total = BitcoinMoney.sats(100_000),
      fee = BitcoinMoney.sats(1000),
      confirmationTime = null,
      inputs = immutableListOf(
        BdkTxIn(outpoint = BdkOutPoint("abc", 0u), sequence = 0u, witness = emptyList())
      )
    ).copy(vsize = 0uL)

    val result = service.prepareTransactionSpeedUp(FullAccountMock, transaction)

    result.shouldBeErrOfType<SpeedUpTransactionError.FailedToPrepareData>()
  }

  test("uses vsize fallback when transaction weight is null") {
    feeRateEstimator.estimatedFeeRateResult = FeeRate(1.0f) // Low rate so BIP125 minimum is used

    val transaction = BitcoinTransactionMock(
      txid = "null-weight-tx",
      total = BitcoinMoney.sats(100_000),
      fee = BitcoinMoney.sats(1000),
      confirmationTime = null,
      inputs = immutableListOf(
        BdkTxIn(outpoint = BdkOutPoint("abc", 0u), sequence = 0u, witness = emptyList())
      )
    ).copy(
      vsize = 80uL,
      weight = null
    )

    val result = service.prepareTransactionSpeedUp(FullAccountMock, transaction)

    result.shouldBeOk()
    // vsize=80 -> fallback weight=320; min BIP125 fee rate = 13.5 sat/vB
    result.value.newFeeRate.satsPerVByte.shouldBe(13.5f)
  }

  test("returns FailedToPrepareData when transaction weight is zero") {
    val transaction = BitcoinTransactionMock(
      txid = "zero-weight-tx",
      total = BitcoinMoney.sats(100_000),
      fee = BitcoinMoney.sats(1000),
      confirmationTime = null,
      inputs = immutableListOf(
        BdkTxIn(outpoint = BdkOutPoint("abc", 0u), sequence = 0u, witness = emptyList())
      )
    ).copy(weight = 0uL)

    val result = service.prepareTransactionSpeedUp(FullAccountMock, transaction)

    result.shouldBeErrOfType<SpeedUpTransactionError.FailedToPrepareData>()
  }

  test("returns FailedToPrepareData when transaction recipientAddress is null") {
    val transaction = BitcoinTransactionMock(
      txid = "no-recipient-tx",
      total = BitcoinMoney.sats(100_000),
      fee = BitcoinMoney.sats(1000),
      confirmationTime = null,
      inputs = immutableListOf(
        BdkTxIn(outpoint = BdkOutPoint("abc", 0u), sequence = 0u, witness = emptyList())
      )
    ).copy(recipientAddress = null)

    val result = service.prepareTransactionSpeedUp(FullAccountMock, transaction)

    result.shouldBeErrOfType<SpeedUpTransactionError.FailedToPrepareData>()
  }

  test("returns FailedToPrepareData when spending wallet is unavailable") {
    bitcoinWalletService.spendingWallet.value = null

    val transaction = BitcoinTransactionMock(
      txid = "tx",
      total = BitcoinMoney.sats(100_000),
      fee = BitcoinMoney.sats(1000),
      confirmationTime = null,
      inputs = immutableListOf(
        BdkTxIn(outpoint = BdkOutPoint("abc", 0u), sequence = 0u, witness = emptyList())
      )
    )

    val result = service.prepareTransactionSpeedUp(FullAccountMock, transaction)

    result.shouldBeErrOfType<SpeedUpTransactionError.FailedToPrepareData>()
  }

  test("returns InsufficientFunds when BDK returns InsufficientFunds error") {
    spendingWallet.createSignedPsbtResult = Err(BdkError.InsufficientFunds(null, null))

    val transaction = BitcoinTransactionMock(
      txid = "insufficient-funds-tx",
      total = BitcoinMoney.sats(100_000),
      fee = BitcoinMoney.sats(1000),
      confirmationTime = null,
      inputs = immutableListOf(
        BdkTxIn(outpoint = BdkOutPoint("abc", 0u), sequence = 0u, witness = emptyList())
      )
    )

    val result = service.prepareTransactionSpeedUp(FullAccountMock, transaction)

    result.shouldBeErrOfType<SpeedUpTransactionError.InsufficientFunds>()
  }

  test("returns FeeRateTooLow when BDK returns FeeRateTooLow error") {
    spendingWallet.createSignedPsbtResult = Err(BdkError.FeeRateTooLow(null, null))

    val transaction = BitcoinTransactionMock(
      txid = "fee-too-low-tx",
      total = BitcoinMoney.sats(100_000),
      fee = BitcoinMoney.sats(1000),
      confirmationTime = null,
      inputs = immutableListOf(
        BdkTxIn(outpoint = BdkOutPoint("abc", 0u), sequence = 0u, witness = emptyList())
      )
    )

    val result = service.prepareTransactionSpeedUp(FullAccountMock, transaction)

    result.shouldBeErrOfType<SpeedUpTransactionError.FeeRateTooLow>()
  }

  test("uses 5x fee rate for Signet test accounts") {
    val signetAccount = FullAccount(
      accountId = FullAccountMock.accountId,
      config = FullAccountConfigMock.copy(
        isTestAccount = true,
        bitcoinNetworkType = SIGNET
      ),
      keybox = KeyboxMock
    )

    feeRateEstimator.estimatedFeeRateResult = FeeRate(2.0f)

    // Default vsize = 325 / 4 = 81 vbytes
    val transaction = BitcoinTransactionMock(
      txid = "signet-tx",
      total = BitcoinMoney.sats(100_000),
      fee = BitcoinMoney.sats(162), // 2 sats per vbyte for 81 vbytes
      confirmationTime = null,
      inputs = immutableListOf(
        BdkTxIn(outpoint = BdkOutPoint("abc", 0u), sequence = 0u, witness = emptyList())
      )
    )

    val result = service.prepareTransactionSpeedUp(signetAccount, transaction)

    result.shouldBeOk()
    // Expected: 5x multiplier = 10 sats/vB
    result.value.newFeeRate.satsPerVByte.shouldBe(10.0f)
  }

  test("does not apply 5x multiplier for mainnet accounts") {
    val mainnetAccount = FullAccount(
      accountId = FullAccountMock.accountId,
      config = FullAccountConfigMock.copy(
        isTestAccount = false,
        bitcoinNetworkType = BITCOIN
      ),
      keybox = KeyboxMock
    )

    feeRateEstimator.estimatedFeeRateResult = FeeRate(15.0f)

    // Default vsize = 325 / 4 = 81 vbytes
    val transaction = BitcoinTransactionMock(
      txid = "mainnet-tx",
      total = BitcoinMoney.sats(100_000),
      fee = BitcoinMoney.sats(810),
      confirmationTime = null,
      inputs = immutableListOf(
        BdkTxIn(outpoint = BdkOutPoint("abc", 0u), sequence = 0u, witness = emptyList())
      )
    )

    val result = service.prepareTransactionSpeedUp(mainnetAccount, transaction)

    result.shouldBeOk()
    // Should use estimated fee rate (15.0) since it's higher than BIP125 minimum
    result.value.newFeeRate.satsPerVByte.shouldBe(15.0f)
  }

  test("calculates minimum BIP125 fee rate correctly based on original transaction") {
    feeRateEstimator.estimatedFeeRateResult = FeeRate(5.0f)

    // Use a low fee so BIP125 minimum is higher than network estimate
    val transaction = BitcoinTransactionMock(
      txid = "bip125-calc-tx",
      total = BitcoinMoney.sats(100_000),
      fee = BitcoinMoney.sats(340),
      confirmationTime = null,
      inputs = immutableListOf(
        BdkTxIn(outpoint = BdkOutPoint("abc", 0u), sequence = 0u, witness = emptyList())
      )
    )

    val result = service.prepareTransactionSpeedUp(FullAccountMock, transaction)

    result.shouldBeOk()
    // Should use BIP125 minimum since network estimate (5.0) is lower
    // BIP125 requires: originalFee + (1 sat/vB * vsize)
    val expectedRate = result.value.newFeeRate.satsPerVByte
    // Verify it's higher than the network estimate
    (expectedRate > 5.0f).shouldBeTrue()
  }

  test("detects RBF signal with any input sequence below threshold") {
    val transaction = BitcoinTransactionMock(
      txid = "multi-input-rbf-tx",
      total = BitcoinMoney.sats(100_000),
      fee = BitcoinMoney.sats(1000),
      confirmationTime = null,
      inputs = immutableListOf(
        BdkTxIn(outpoint = BdkOutPoint("abc", 0u), sequence = UInt.MAX_VALUE, witness = emptyList()),
        BdkTxIn(outpoint = BdkOutPoint("def", 1u), sequence = UInt.MAX_VALUE - 2u, witness = emptyList()),
        BdkTxIn(outpoint = BdkOutPoint("ghi", 2u), sequence = UInt.MAX_VALUE, witness = emptyList())
      )
    )

    val result = service.prepareTransactionSpeedUp(FullAccountMock, transaction)

    result.shouldBeOk()
  }

  test("ignores confirmed descendants when checking for unconfirmed children") {
    val parentTx = BitcoinTransactionMock(
      txid = "parent-tx",
      total = BitcoinMoney.sats(100_000),
      fee = BitcoinMoney.sats(1000),
      confirmationTime = null,
      inputs = immutableListOf(
        BdkTxIn(outpoint = BdkOutPoint("abc", 0u), sequence = 0u, witness = emptyList())
      )
    )

    val confirmedChildTx = BitcoinTransactionMock(
      txid = "confirmed-child-tx",
      total = BitcoinMoney.sats(50_000),
      fee = BitcoinMoney.sats(500),
      confirmationTime = build.wallet.time.someInstant, // Confirmed
      inputs = immutableListOf(
        BdkTxIn(outpoint = BdkOutPoint("parent-tx", 0u), sequence = 0u, witness = emptyList())
      )
    )

    bitcoinWalletService.setTransactions(listOf(parentTx, confirmedChildTx))

    val result = service.prepareTransactionSpeedUp(FullAccountMock, parentTx)

    // Should succeed because confirmed child doesn't block speed-up
    result.shouldBeOk()
  }

  test("handles empty transaction list when checking for descendants") {
    bitcoinWalletService.transactionsData.value = null
    bitcoinWalletService.setTransactions(emptyList())

    val transaction = BitcoinTransactionMock(
      txid = "empty-list-tx",
      total = BitcoinMoney.sats(100_000),
      fee = BitcoinMoney.sats(1000),
      confirmationTime = null,
      inputs = immutableListOf(
        BdkTxIn(outpoint = BdkOutPoint("abc", 0u), sequence = 0u, witness = emptyList())
      )
    )

    val result = service.prepareTransactionSpeedUp(FullAccountMock, transaction)

    result.shouldBeOk()
  }

  test("returns FailedToPrepareData for other BDK errors") {
    spendingWallet.createSignedPsbtResult = Err(BdkError.Generic(null, "Unknown error"))

    val transaction = BitcoinTransactionMock(
      txid = "other-error-tx",
      total = BitcoinMoney.sats(100_000),
      fee = BitcoinMoney.sats(1000),
      confirmationTime = null,
      inputs = immutableListOf(
        BdkTxIn(outpoint = BdkOutPoint("abc", 0u), sequence = 0u, witness = emptyList())
      )
    )

    val result = service.prepareTransactionSpeedUp(FullAccountMock, transaction)

    result.shouldBeErrOfType<SpeedUpTransactionError.FailedToPrepareData>()
  }

  test("correctly identifies transaction without RBF signal when all inputs at max sequence") {
    val transaction = BitcoinTransactionMock(
      txid = "no-rbf-all-max-tx",
      total = BitcoinMoney.sats(100_000),
      fee = BitcoinMoney.sats(1000),
      confirmationTime = null,
      inputs = immutableListOf(
        BdkTxIn(outpoint = BdkOutPoint("abc", 0u), sequence = UInt.MAX_VALUE, witness = emptyList()),
        BdkTxIn(outpoint = BdkOutPoint("def", 1u), sequence = UInt.MAX_VALUE, witness = emptyList())
      )
    )

    val result = service.prepareTransactionSpeedUp(FullAccountMock, transaction)

    result.shouldBeErrOfType<SpeedUpTransactionError.TransactionNotReplaceable>()
  }

  test("correctly identifies RBF signal when input sequence is exactly at threshold minus one") {
    val transaction = BitcoinTransactionMock(
      txid = "rbf-at-threshold-tx",
      total = BitcoinMoney.sats(100_000),
      fee = BitcoinMoney.sats(1000),
      confirmationTime = null,
      inputs = immutableListOf(
        BdkTxIn(
          outpoint = BdkOutPoint("abc", 0u),
          sequence = UInt.MAX_VALUE - 1u, // Exactly at the threshold
          witness = emptyList()
        )
      )
    )

    val result = service.prepareTransactionSpeedUp(FullAccountMock, transaction)

    // Sequence < 0xFFFFFFFE signals RBF, but 0xFFFFFFFE itself does not
    result.shouldBeErrOfType<SpeedUpTransactionError.TransactionNotReplaceable>()
  }

  test("handles transaction with zero sequence correctly") {
    val transaction = BitcoinTransactionMock(
      txid = "zero-sequence-tx",
      total = BitcoinMoney.sats(100_000),
      fee = BitcoinMoney.sats(1000),
      confirmationTime = null,
      inputs = immutableListOf(
        BdkTxIn(outpoint = BdkOutPoint("abc", 0u), sequence = 0u, witness = emptyList())
      )
    )

    val result = service.prepareTransactionSpeedUp(FullAccountMock, transaction)

    result.shouldBeOk()
    (transaction.inputs.first().sequence < UInt.MAX_VALUE - 1u).shouldBeTrue()
  }

  context("Manual fee bump (BDK2 with output shrinking)") {
    test("uses manual fee bump path when BDK2 enabled and shrinking is needed") {
      bdk2FeatureFlag.setBdk2Enabled(true)
      feeBumpAllowShrinkingChecker.shrinkingOutput = BdkScriptMock()

      // Single-output sweep transaction
      val transaction = BitcoinTransactionMock(
        txid = "sweep-tx",
        total = BitcoinMoney.sats(10_000),
        fee = BitcoinMoney.sats(200),
        confirmationTime = null,
        inputs = immutableListOf(
          BdkTxIn(outpoint = BdkOutPoint("abc", 0u), sequence = 0xFFFFFFFDu, witness = emptyList())
        ),
        outputs = immutableListOf(
          BdkTxOutMock.copy(value = 9800u) // total - fee
        )
      )

      // Mock PSBT must have fee rate > old fee rate (200/81 ≈ 2.47 sat/vB)
      // With vsize=81 and fee=810, fee rate = 10 sat/vB > 2.47 sat/vB
      spendingWallet.createSignedPsbtResult = Ok(
        PsbtMock.copy(
          fee = Fee(BitcoinMoney.sats(810)),
          vsize = 81
        )
      )

      val result = service.prepareTransactionSpeedUp(FullAccountMock, transaction)

      result.shouldBeOk()
    }

    test("manual fee bump succeeds when clamped to BIP125 minimum after low requested fee") {
      bdk2FeatureFlag.setBdk2Enabled(true)
      feeBumpAllowShrinkingChecker.shrinkingOutput = BdkScriptMock()
      feeRateEstimator.estimatedFeeRateResult = FeeRate(1.0f) // Low rate -> initial fee below old

      // Old fee = 500 sats, vsize = 81 vbytes -> minBip125Fee = 581 sats (> old fee).
      val transaction = BitcoinTransactionMock(
        txid = "clamp-success-tx",
        total = BitcoinMoney.sats(10_000),
        fee = BitcoinMoney.sats(500),
        confirmationTime = null,
        inputs = immutableListOf(
          BdkTxIn(outpoint = BdkOutPoint("abc", 0u), sequence = 0xFFFFFFFDu, witness = emptyList())
        ),
        outputs = immutableListOf(
          BdkTxOutMock.copy(value = 9_500u)
        )
      )

      // Mock PSBT with fee rate higher than original (600/81 > 500/81) so post-check passes.
      spendingWallet.createSignedPsbtResult = Ok(
        PsbtMock.copy(
          fee = Fee(BitcoinMoney.sats(600)),
          vsize = 81
        )
      )

      val result = service.prepareTransactionSpeedUp(FullAccountMock, transaction)

      result.shouldBeOk()
    }

    test("uses regular BumpFee path when BDK2 enabled but no shrinking needed") {
      bdk2FeatureFlag.setBdk2Enabled(true)
      feeBumpAllowShrinkingChecker.shrinkingOutput = null // No shrinking needed

      val transaction = BitcoinTransactionMock(
        txid = "regular-tx",
        total = BitcoinMoney.sats(100_000),
        fee = BitcoinMoney.sats(1000),
        confirmationTime = null,
        inputs = immutableListOf(
          BdkTxIn(outpoint = BdkOutPoint("abc", 0u), sequence = 0u, witness = emptyList())
        )
      )

      val result = service.prepareTransactionSpeedUp(FullAccountMock, transaction)

      result.shouldBeOk()
    }

    test("uses regular BumpFee path when BDK2 disabled even if shrinking would be needed") {
      bdk2FeatureFlag.setBdk2Enabled(false)
      feeBumpAllowShrinkingChecker.shrinkingOutput = BdkScriptMock() // Would need shrinking

      val transaction = BitcoinTransactionMock(
        txid = "legacy-tx",
        total = BitcoinMoney.sats(100_000),
        fee = BitcoinMoney.sats(1000),
        confirmationTime = null,
        inputs = immutableListOf(
          BdkTxIn(outpoint = BdkOutPoint("abc", 0u), sequence = 0u, witness = emptyList())
        )
      )

      val result = service.prepareTransactionSpeedUp(FullAccountMock, transaction)

      result.shouldBeOk()
    }

    test("uses regular BumpFee path when UTXO data is unavailable") {
      bdk2FeatureFlag.setBdk2Enabled(true)
      feeBumpAllowShrinkingChecker.shrinkingOutput = BdkScriptMock() // Would need shrinking
      bitcoinWalletService.transactionsData.value = null // No UTXO data available

      val transaction = BitcoinTransactionMock(
        txid = "no-utxo-data-tx",
        total = BitcoinMoney.sats(100_000),
        fee = BitcoinMoney.sats(1000),
        confirmationTime = null,
        inputs = immutableListOf(
          BdkTxIn(outpoint = BdkOutPoint("abc", 0u), sequence = 0u, witness = emptyList())
        )
      )

      val result = service.prepareTransactionSpeedUp(FullAccountMock, transaction)

      // Falls back to BumpFee path which succeeds with default mock
      result.shouldBeOk()
    }

    test("uses manual fee bump when only unconfirmed UTXOs exist") {
      bdk2FeatureFlag.setBdk2Enabled(true)

      bitcoinWalletService.transactionsData.value = TransactionsDataMock.copy(
        utxos = Utxos(
          confirmed = emptySet(),
          unconfirmed = setOf(BdkUtxoMock)
        )
      )

      val serviceWithRealChecker = SpeedUpTransactionServiceImpl(
        feeRateEstimator = feeRateEstimator,
        bitcoinWalletService = bitcoinWalletService,
        feeBumpAllowShrinkingChecker = FeeBumpAllowShrinkingCheckerImpl(),
        bdk2FeatureFlag = bdk2FeatureFlag
      )

      val transaction = BitcoinTransactionMock(
        txid = "pending-sendall-tx",
        total = BitcoinMoney.sats(10_000),
        fee = BitcoinMoney.sats(200),
        confirmationTime = null,
        inputs = immutableListOf(
          BdkTxIn(outpoint = BdkOutPoint("abc", 0u), sequence = 0xFFFFFFFDu, witness = emptyList())
        ),
        outputs = immutableListOf(
          BdkTxOutMock.copy(value = 9_800u)
        )
      )

      spendingWallet.createSignedPsbtResult = Ok(
        PsbtMock.copy(
          fee = Fee(BitcoinMoney.sats(810)),
          vsize = 81
        )
      )

      val result = serviceWithRealChecker.prepareTransactionSpeedUp(FullAccountMock, transaction)

      result.shouldBeOk()
      spendingWallet.lastCreateSignedPsbtConstructionType
        .shouldBeInstanceOf<SpendingWallet.PsbtConstructionMethod.ManualFeeBump>()
    }

    test("uses regular fee bump when confirmed UTXO exists") {
      bdk2FeatureFlag.setBdk2Enabled(true)

      bitcoinWalletService.transactionsData.value = TransactionsDataMock.copy(
        utxos = Utxos(
          confirmed = setOf(BdkUtxoMock),
          unconfirmed = emptySet()
        )
      )

      val serviceWithRealChecker = SpeedUpTransactionServiceImpl(
        feeRateEstimator = feeRateEstimator,
        bitcoinWalletService = bitcoinWalletService,
        feeBumpAllowShrinkingChecker = FeeBumpAllowShrinkingCheckerImpl(),
        bdk2FeatureFlag = bdk2FeatureFlag
      )

      val transaction = BitcoinTransactionMock(
        txid = "confirmed-utxo-sendall-tx",
        total = BitcoinMoney.sats(10_000),
        fee = BitcoinMoney.sats(200),
        confirmationTime = null,
        inputs = immutableListOf(
          BdkTxIn(outpoint = BdkOutPoint("abc", 0u), sequence = 0xFFFFFFFDu, witness = emptyList())
        ),
        outputs = immutableListOf(
          BdkTxOutMock.copy(value = 9_800u)
        )
      )

      val result = serviceWithRealChecker.prepareTransactionSpeedUp(FullAccountMock, transaction)

      result.shouldBeOk()
      spendingWallet.lastCreateSignedPsbtConstructionType
        .shouldBeInstanceOf<SpendingWallet.PsbtConstructionMethod.FeeBump>()
    }

    test("uses regular fee bump when both confirmed and unconfirmed UTXOs exist") {
      bdk2FeatureFlag.setBdk2Enabled(true)

      bitcoinWalletService.transactionsData.value = TransactionsDataMock.copy(
        utxos = Utxos(
          confirmed = setOf(BdkUtxoMock),
          unconfirmed = setOf(BdkUtxoMock2)
        )
      )

      val serviceWithRealChecker = SpeedUpTransactionServiceImpl(
        feeRateEstimator = feeRateEstimator,
        bitcoinWalletService = bitcoinWalletService,
        feeBumpAllowShrinkingChecker = FeeBumpAllowShrinkingCheckerImpl(),
        bdk2FeatureFlag = bdk2FeatureFlag
      )

      val transaction = BitcoinTransactionMock(
        txid = "mixed-utxo-sendall-tx",
        total = BitcoinMoney.sats(10_000),
        fee = BitcoinMoney.sats(200),
        confirmationTime = null,
        inputs = immutableListOf(
          BdkTxIn(outpoint = BdkOutPoint("abc", 0u), sequence = 0xFFFFFFFDu, witness = emptyList())
        ),
        outputs = immutableListOf(
          BdkTxOutMock.copy(value = 9_800u)
        )
      )

      val result = serviceWithRealChecker.prepareTransactionSpeedUp(FullAccountMock, transaction)

      result.shouldBeOk()
      spendingWallet.lastCreateSignedPsbtConstructionType
        .shouldBeInstanceOf<SpendingWallet.PsbtConstructionMethod.FeeBump>()
    }

    test("returns OutputBelowDustLimit when BDK rejects due to dust") {
      bdk2FeatureFlag.setBdk2Enabled(true)
      feeBumpAllowShrinkingChecker.shrinkingOutput = BdkScriptMock()
      // Configure mock to return dust error from BDK
      spendingWallet.createSignedPsbtResult = Err(BdkError.OutputBelowDustLimit(null, null))

      val transaction = BitcoinTransactionMock(
        txid = "small-sweep-tx",
        total = BitcoinMoney.sats(500),
        fee = BitcoinMoney.sats(100),
        confirmationTime = null,
        inputs = immutableListOf(
          BdkTxIn(outpoint = BdkOutPoint("abc", 0u), sequence = 0xFFFFFFFDu, witness = emptyList())
        ),
        outputs = immutableListOf(
          BdkTxOutMock.copy(value = 400u)
        )
      )

      val result = service.prepareTransactionSpeedUp(FullAccountMock, transaction)

      result.shouldBeErrOfType<SpeedUpTransactionError.OutputBelowDustLimit>()
    }

    test("returns FeeRateTooLow when target fee rate <= old fee rate") {
      bdk2FeatureFlag.setBdk2Enabled(true)
      feeBumpAllowShrinkingChecker.shrinkingOutput = BdkScriptMock()
      feeRateEstimator.estimatedFeeRateResult = FeeRate(1.0f) // Very low fee rate

      // Transaction with high existing fee rate
      val transaction = BitcoinTransactionMock(
        txid = "high-fee-tx",
        total = BitcoinMoney.sats(10_000),
        fee = BitcoinMoney.sats(500), // High fee = ~6.17 sat/vB
        confirmationTime = null,
        inputs = immutableListOf(
          BdkTxIn(outpoint = BdkOutPoint("abc", 0u), sequence = 0xFFFFFFFDu, witness = emptyList())
        ),
        outputs = immutableListOf(
          BdkTxOutMock.copy(value = 9500u)
        )
      )

      val result = service.prepareTransactionSpeedUp(FullAccountMock, transaction)

      result.shouldBeErrOfType<SpeedUpTransactionError.FeeRateTooLow>()
    }

    test("returns FailedToPrepareData when wallet returns SpendingWalletV2Error") {
      bdk2FeatureFlag.setBdk2Enabled(true)
      feeBumpAllowShrinkingChecker.shrinkingOutput = BdkScriptMock()
      // Configure mock to return a wallet error
      spendingWallet.createSignedPsbtResult = Err(SpendingWalletV2Error.NotImplemented("test"))

      val transaction = BitcoinTransactionMock(
        txid = "wallet-error-tx",
        total = BitcoinMoney.sats(10_000),
        fee = BitcoinMoney.sats(200),
        confirmationTime = null,
        inputs = immutableListOf(
          BdkTxIn(outpoint = BdkOutPoint("abc", 0u), sequence = 0xFFFFFFFDu, witness = emptyList())
        ),
        outputs = immutableListOf(
          BdkTxOutMock.copy(value = 9800u)
        )
      )

      val result = service.prepareTransactionSpeedUp(FullAccountMock, transaction)

      result.shouldBeErrOfType<SpeedUpTransactionError.FailedToPrepareData>()
    }
  }
})
