package build.wallet.bitcoin.transactions

import build.wallet.bdk.bindings.BdkError
import build.wallet.bdk.bindings.BdkOutPoint
import build.wallet.bdk.bindings.BdkTxIn
import build.wallet.bitcoin.BitcoinNetworkType.BITCOIN
import build.wallet.bitcoin.BitcoinNetworkType.SIGNET
import build.wallet.bitcoin.fees.BitcoinFeeRateEstimatorMock
import build.wallet.bitcoin.fees.FeeRate
import build.wallet.bitcoin.wallet.SpendingWalletMock
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.keybox.FullAccountConfigMock
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.compose.collections.immutableListOf
import build.wallet.coroutines.turbine.turbines
import build.wallet.money.BitcoinMoney
import build.wallet.testing.shouldBeErrOfType
import build.wallet.testing.shouldBeOk
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe

class SpeedUpTransactionServiceImplTests : FunSpec({
  val feeRateEstimator = BitcoinFeeRateEstimatorMock()
  val bitcoinWalletService = BitcoinWalletServiceFake()
  val spendingWallet = SpendingWalletMock(turbines::create)

  val service = SpeedUpTransactionServiceImpl(
    feeRateEstimator = feeRateEstimator,
    bitcoinWalletService = bitcoinWalletService
  )

  beforeTest {
    feeRateEstimator.estimatedFeeRateResult = FeeRate(10.0f)
    bitcoinWalletService.spendingWallet.value = spendingWallet
    bitcoinWalletService.setTransactions(emptyList())
    spendingWallet.createSignedPsbtResult = Ok(PsbtMock)
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
})
