package build.wallet.bitcoin.transactions

import build.wallet.bdk.bindings.BdkScriptMock
import build.wallet.bdk.bindings.BdkTxOut
import build.wallet.bitcoin.transactions.BitcoinTransaction.TransactionType.Incoming
import build.wallet.bitcoin.transactions.BitcoinTransaction.TransactionType.Outgoing
import build.wallet.compose.collections.emptyImmutableList
import build.wallet.compose.collections.immutableListOf
import build.wallet.money.BitcoinMoney
import build.wallet.time.someInstant
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue

class BitcoinTransactionBumpabilityCheckerImplTests : FunSpec({
  val sweepChecker = BitcoinTransactionSweepCheckerImpl()
  val bumpabilityChecker = BitcoinTransactionBumpabilityCheckerImpl(sweepChecker)

  test("bumpable if not a sweep, is Outgoing, and Pending") {
    val transaction = BitcoinTransactionMock(
      total = BitcoinMoney.sats(100),
      inputs = immutableListOf(),
      outputs = immutableListOf(
        // Two outputs == not a sweep
        BdkTxOut(value = 100u, scriptPubkey = BdkScriptMock()),
        BdkTxOut(value = 200u, scriptPubkey = BdkScriptMock())
      ),
      confirmationTime = null,
      transactionType = Outgoing
    )

    bumpabilityChecker.isBumpable(
      transaction = transaction,
      walletUnspentOutputs = emptyImmutableList()
    ).shouldBeTrue()
  }

  test("not bumpable if not pending") {
    val transaction = BitcoinTransactionMock(
      total = BitcoinMoney.sats(100),
      inputs = immutableListOf(),
      outputs = immutableListOf(
        // Two outputs == not a sweep
        BdkTxOut(value = 100u, scriptPubkey = BdkScriptMock()),
        BdkTxOut(value = 200u, scriptPubkey = BdkScriptMock())
      ),
      confirmationTime = someInstant,
      transactionType = Outgoing
    )

    bumpabilityChecker.isBumpable(
      transaction = transaction,
      walletUnspentOutputs = emptyImmutableList()
    ).shouldBeFalse()
  }

  test("not bumpable if not Outgoing") {
    val transaction = BitcoinTransactionMock(
      total = BitcoinMoney.sats(100),
      inputs = immutableListOf(),
      outputs = immutableListOf(
        // Two outputs == not a sweep
        BdkTxOut(value = 100u, scriptPubkey = BdkScriptMock()),
        BdkTxOut(value = 200u, scriptPubkey = BdkScriptMock())
      ),
      confirmationTime = null,
      transactionType = Incoming
    )

    bumpabilityChecker.isBumpable(
      transaction = transaction,
      walletUnspentOutputs = emptyImmutableList()
    ).shouldBeFalse()
  }

  test("not bumpable if a sweep") {
    val transaction = BitcoinTransactionMock(
      total = BitcoinMoney.sats(100),
      inputs = immutableListOf(),
      outputs = immutableListOf(
        // One output + no walletUnspentOutputs == a sweep
        BdkTxOut(value = 100u, scriptPubkey = BdkScriptMock())
      ),
      confirmationTime = null,
      transactionType = Outgoing
    )

    bumpabilityChecker.isBumpable(
      transaction = transaction,
      walletUnspentOutputs = emptyImmutableList()
    ).shouldBeFalse()
  }
})
