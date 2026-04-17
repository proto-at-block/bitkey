package build.wallet.bitcoin.transactions

import build.wallet.account.AccountServiceFake
import build.wallet.bdk.bindings.BdkOutPoint
import build.wallet.bdk.bindings.BdkScriptMock
import build.wallet.bdk.bindings.BdkTxIn
import build.wallet.bdk.bindings.BdkTxOut
import build.wallet.bdk.bindings.BdkUtxo
import build.wallet.bitcoin.balance.BitcoinBalance
import build.wallet.bitcoin.fees.BitcoinFeeRateEstimatorMock
import build.wallet.bitcoin.fees.Fee
import build.wallet.bitcoin.fees.FeeRate
import build.wallet.bitcoin.transactions.BitcoinTransaction.ConfirmationStatus.Pending
import build.wallet.bitcoin.transactions.BitcoinTransaction.TransactionType.Incoming
import build.wallet.bitcoin.transactions.BitcoinTransaction.TransactionType.Outgoing
import build.wallet.bitcoin.transactions.BitcoinTransaction.TransactionType.UtxoConsolidation
import build.wallet.bitcoin.utxo.Utxos
import build.wallet.bitcoin.wallet.SpendingWallet.PsbtConstructionMethod
import build.wallet.bitcoin.wallet.SpendingWalletMock
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.compose.collections.emptyImmutableList
import build.wallet.coroutines.turbine.turbines
import build.wallet.money.BitcoinMoney
import build.wallet.testing.shouldBeErrOfType
import build.wallet.time.someInstant
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.collections.immutable.persistentListOf

class SpeedUpDepositServiceImplTests : FunSpec({
  val feeRateEstimator = BitcoinFeeRateEstimatorMock()
  val bitcoinWalletService = BitcoinWalletServiceFake()
  val accountService = AccountServiceFake()
  val spendingWallet = SpendingWalletMock(turbines::create)

  val service = SpeedUpDepositServiceImpl(
    feeRateEstimator = feeRateEstimator,
    bitcoinWalletService = bitcoinWalletService,
    accountService = accountService
  )

  beforeTest {
    feeRateEstimator.reset()
    bitcoinWalletService.reset()
    accountService.reset()
    spendingWallet.reset()
    // Set a full account as the active account by default
    accountService.setActiveAccount(FullAccountMock)
  }

  test("fails if transaction is outgoing") {
    val transaction = BitcoinTransactionMock(
      total = BitcoinMoney.sats(10_000),
      fee = BitcoinMoney.sats(500),
      transactionType = Outgoing,
      confirmationTime = null
    )

    val result = service.prepareSpeedUpDepositTransaction(transaction)

    result.shouldBeErrOfType<Error>()
      .message.shouldBe("CPFP failed: transaction is not incoming")
  }

  test("fails if transaction is already confirmed") {
    val transaction = BitcoinTransaction(
      id = "confirmed-tx",
      recipientAddress = null,
      broadcastTime = null,
      estimatedConfirmationTime = null,
      confirmationStatus = BitcoinTransaction.ConfirmationStatus.Confirmed(
        build.wallet.bitcoin.BlockTime(1, someInstant)
      ),
      total = BitcoinMoney.sats(10_000),
      subtotal = BitcoinMoney.sats(10_000),
      fee = BitcoinMoney.sats(500),
      weight = 400uL,
      vsize = 100uL,
      transactionType = Incoming,
      inputs = emptyImmutableList(),
      outputs = emptyImmutableList()
    )

    val result = service.prepareSpeedUpDepositTransaction(transaction)

    result.shouldBeErrOfType<Error>()
      .message.shouldBe("CPFP failed: transaction is already confirmed")
  }

  test("fails if parent fee is missing") {
    val transaction = BitcoinTransaction(
      id = "no-fee-tx",
      recipientAddress = null,
      broadcastTime = null,
      estimatedConfirmationTime = null,
      confirmationStatus = Pending,
      total = BitcoinMoney.sats(10_000),
      subtotal = BitcoinMoney.sats(10_000),
      fee = null,
      weight = 400uL,
      vsize = 100uL,
      transactionType = Incoming,
      inputs = emptyImmutableList(),
      outputs = emptyImmutableList()
    )

    val result = service.prepareSpeedUpDepositTransaction(transaction)

    result.shouldBeErrOfType<Error>()
      .message.shouldBe("CPFP failed: parent transaction missing fee")
  }

  test("fails if no unconfirmed UTXO matches parent transaction") {
    feeRateEstimator.estimatedFeeRateResult = FeeRate(satsPerVByte = 10f)

    val transaction = BitcoinTransaction(
      id = "parent-tx-id",
      recipientAddress = null,
      broadcastTime = null,
      estimatedConfirmationTime = null,
      confirmationStatus = Pending,
      total = BitcoinMoney.sats(10_000),
      subtotal = BitcoinMoney.sats(10_000),
      fee = BitcoinMoney.sats(200),
      weight = 400uL,
      vsize = 100uL,
      transactionType = Incoming,
      inputs = emptyImmutableList(),
      outputs = emptyImmutableList()
    )

    bitcoinWalletService.spendingWallet.value = spendingWallet

    // Set up wallet data with unconfirmed UTXOs from a different transaction
    bitcoinWalletService.transactionsData.value = TransactionsData(
      balance = BitcoinBalance.ZeroBalance,
      fiatBalance = null,
      transactions = emptyImmutableList(),
      utxos = Utxos(
        confirmed = emptySet(),
        unconfirmed = setOf(
          BdkUtxo(
            outPoint = BdkOutPoint("different-tx-id", 0u),
            txOut = BdkTxOut(value = 10_000u, scriptPubkey = BdkScriptMock()),
            isSpent = false
          )
        )
      )
    )

    val result = service.prepareSpeedUpDepositTransaction(transaction)

    result.shouldBeErrOfType<Error>()
      .message.shouldBe("CPFP failed: no spendable UTXO found in CPFP chain for parent tx")
  }

  test("successfully prepares a CPFP speed-up transaction") {
    // Parent: fee=100 sats, weight=400 WU (1 sat/vB)
    // Target rate: 5 sat/vB → child must bring the package up to that rate.
    // With dummy psbt vsize=100 (400 WU), the required child fee works out to:
    //   totalVbytes = ceil((400 + 400) / 4) = 200 vbytes
    //   requiredTotalFee = ceil(5.0 * 200) = 1000 sats
    //   childFee = 1000 - 100 = 900 sats
    // The mock returns a psbt with fee=500 sats (it ignores the absolute fee policy).
    feeRateEstimator.estimatedFeeRateResult = FeeRate(satsPerVByte = 5f)

    val transaction = BitcoinTransaction(
      id = "parent-tx-id",
      recipientAddress = null,
      broadcastTime = null,
      estimatedConfirmationTime = null,
      confirmationStatus = Pending,
      total = BitcoinMoney.sats(50_000),
      subtotal = BitcoinMoney.sats(50_000),
      fee = BitcoinMoney.sats(100),
      weight = 400uL,
      vsize = 100uL,
      transactionType = Incoming,
      inputs = emptyImmutableList(),
      outputs = emptyImmutableList()
    )

    bitcoinWalletService.spendingWallet.value = spendingWallet

    // UTXO matching the parent outpoint; value = 50_000 sats
    bitcoinWalletService.transactionsData.value = TransactionsData(
      balance = BitcoinBalance.ZeroBalance,
      fiatBalance = null,
      transactions = emptyImmutableList(),
      utxos = Utxos(
        confirmed = emptySet(),
        unconfirmed = setOf(
          BdkUtxo(
            outPoint = BdkOutPoint("parent-tx-id", 0u),
            txOut = BdkTxOut(value = 50_000u, scriptPubkey = BdkScriptMock()),
            isSpent = false
          )
        )
      )
    )

    // Override the mock to return a controlled PSBT with fee=500 sats
    spendingWallet.createSignedPsbtResult = Ok(
      Psbt(
        id = "cpfp-psbt-id",
        base64 = "cpfp-base64",
        fee = Fee(BitcoinMoney.sats(500)),
        vsize = 100,
        numOfInputs = 1,
        amountSats = 49_500UL
      )
    )

    val result = service.prepareSpeedUpDepositTransaction(transaction)

    result.isOk.shouldBe(true)
    val speedUp = result.value
    speedUp.parentTxid.shouldBe("parent-tx-id")
    speedUp.parentFee.shouldBe(Fee(BitcoinMoney.sats(100)))
    speedUp.childFee.shouldBe(Fee(BitcoinMoney.sats(500)))
    // transferAmount = tipUtxoValue - childFee = 50_000 - 500 = 49_500 sats
    speedUp.transferAmount.shouldBe(BitcoinMoney.sats(49_500))
  }
  test("prefers the best descendant CPFP tip over first matching branch") {
    feeRateEstimator.estimatedFeeRateResult = FeeRate(satsPerVByte = 5f)
    bitcoinWalletService.spendingWallet.value = spendingWallet
    spendingWallet.createSignedPsbtResult = Ok(
      Psbt(
        id = "cpfp-psbt-id",
        base64 = "cpfp-base64",
        fee = Fee(BitcoinMoney.sats(500)),
        vsize = 100,
        numOfInputs = 1,
        amountSats = 49_500UL
      )
    )
    val parentTxId = "parent-tx-id"
    val firstChildTx = BitcoinTransactionMock(
      txid = "child-small",
      total = BitcoinMoney.sats(10_000),
      fee = BitcoinMoney.sats(100),
      transactionType = UtxoConsolidation,
      confirmationTime = null,
      inputs = persistentListOf(
        BdkTxIn(
          outpoint = BdkOutPoint(parentTxId, 0u),
          sequence = 0u,
          witness = emptyList()
        )
      )
    )
    val secondChildTx = BitcoinTransactionMock(
      txid = "child-large",
      total = BitcoinMoney.sats(20_000),
      fee = BitcoinMoney.sats(100),
      transactionType = UtxoConsolidation,
      confirmationTime = null,
      inputs = persistentListOf(
        BdkTxIn(
          outpoint = BdkOutPoint(parentTxId, 1u),
          sequence = 0u,
          witness = emptyList()
        )
      )
    )
    bitcoinWalletService.transactionsData.value = TransactionsData(
      balance = BitcoinBalance.ZeroBalance,
      fiatBalance = null,
      transactions = persistentListOf(firstChildTx, secondChildTx),
      utxos = Utxos(
        confirmed = emptySet(),
        unconfirmed = setOf(
          // Leftover direct parent output.
          BdkUtxo(
            outPoint = BdkOutPoint(parentTxId, 0u),
            txOut = BdkTxOut(value = 3_000u, scriptPubkey = BdkScriptMock()),
            isSpent = false
          ),
          // First child branch tip (small).
          BdkUtxo(
            outPoint = BdkOutPoint("child-small", 0u),
            txOut = BdkTxOut(value = 5_000u, scriptPubkey = BdkScriptMock()),
            isSpent = false
          ),
          // Second child branch tip (large and should be selected).
          BdkUtxo(
            outPoint = BdkOutPoint("child-large", 0u),
            txOut = BdkTxOut(value = 40_000u, scriptPubkey = BdkScriptMock()),
            isSpent = false
          )
        )
      )
    )
    val transaction = BitcoinTransaction(
      id = parentTxId,
      recipientAddress = null,
      broadcastTime = null,
      estimatedConfirmationTime = null,
      confirmationStatus = Pending,
      total = BitcoinMoney.sats(50_000),
      subtotal = BitcoinMoney.sats(50_000),
      fee = BitcoinMoney.sats(100),
      weight = 400uL,
      vsize = 100uL,
      transactionType = Incoming,
      inputs = emptyImmutableList(),
      outputs = emptyImmutableList()
    )
    service.prepareSpeedUpDepositTransaction(transaction).isOk.shouldBe(true)
    val cpfp = spendingWallet.lastCreateSignedPsbtConstructionType as PsbtConstructionMethod.Cpfp
    cpfp.utxoOutpoint.shouldBe(BdkOutPoint("child-large", 0u))
  }

  test("fails if parent fee rate already exceeds target") {
    // Use a near-zero target fee rate so the calculated child fee is negative,
    // regardless of the dummy PSBT vsize returned by the wallet mock (default vsize=10000).
    // With parentFee=5000 sats and targetRate=0.0001 sat/vB, the required package fee
    // is far less than the ancestor fee → childFee < 0 → "original fee is sufficient".
    feeRateEstimator.estimatedFeeRateResult = FeeRate(satsPerVByte = 0.0001f)

    val transaction = BitcoinTransaction(
      id = "high-fee-parent-tx",
      recipientAddress = null,
      broadcastTime = null,
      estimatedConfirmationTime = null,
      confirmationStatus = Pending,
      total = BitcoinMoney.sats(10_000),
      subtotal = BitcoinMoney.sats(10_000),
      fee = BitcoinMoney.sats(5_000),
      weight = 400uL,
      vsize = 100uL,
      transactionType = Incoming,
      inputs = emptyImmutableList(),
      outputs = emptyImmutableList()
    )

    // spendingWallet (SpendingWalletMock) returns a valid PSBT by default, allowing
    // the dummy-PSBT step to succeed so we reach the fee sufficiency check.
    bitcoinWalletService.spendingWallet.value = spendingWallet

    bitcoinWalletService.transactionsData.value = TransactionsData(
      balance = BitcoinBalance.ZeroBalance,
      fiatBalance = null,
      transactions = emptyImmutableList(),
      utxos = Utxos(
        confirmed = emptySet(),
        unconfirmed = setOf(
          BdkUtxo(
            outPoint = BdkOutPoint("high-fee-parent-tx", 0u),
            txOut = BdkTxOut(value = 10_000u, scriptPubkey = BdkScriptMock()),
            isSpent = false
          )
        )
      )
    )

    val result = service.prepareSpeedUpDepositTransaction(transaction)

    result.shouldBeErrOfType<Error>()
      .message.shouldBe("CPFP failed: original fee is sufficient")
  }
})
