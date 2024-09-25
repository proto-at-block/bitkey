package build.wallet.bitcoin.transactions

import build.wallet.bdk.bindings.*
import build.wallet.bitcoin.transactions.BitcoinTransaction.TransactionType.*
import build.wallet.compose.collections.emptyImmutableList
import build.wallet.compose.collections.immutableListOf
import build.wallet.money.BitcoinMoney
import build.wallet.time.someInstant
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue

class BitcoinTransactionBumpabilityCheckerImplTests : FunSpec({
  val sweepChecker = BitcoinTransactionSweepCheckerImpl()
  val feeBumpAllowShrinkingChecker = FeeBumpAllowShrinkingCheckerFake()
  val bumpabilityChecker = BitcoinTransactionBumpabilityCheckerImpl(
    sweepChecker = sweepChecker,
    feeBumpAllowShrinkingChecker = feeBumpAllowShrinkingChecker
  )

  beforeTest {
    feeBumpAllowShrinkingChecker.shrinkingOutput = null
  }

  context("outgoing") {
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
  }

  context("incoming") {
    test("not bumpable if incoming") {
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
  }

  context("sweep") {
    test("not bumpable if a sweep and allow_shrinking disabled") {
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

    test("bumpable if a sweep and allow_shrinking enabled") {
      feeBumpAllowShrinkingChecker.shrinkingOutput = BdkScriptMock()
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
      ).shouldBeTrue()
    }
  }

  context("utxo consolidation") {
    test("not bumpable if a utxo consolidation, <= 1 wallet utxos, and allow_shrinking disabled") {
      val transaction = BitcoinTransactionMock(
        total = BitcoinMoney.sats(100),
        inputs = immutableListOf(),
        outputs = immutableListOf(
          BdkTxOut(value = 100u, scriptPubkey = BdkScriptMock())
        ),
        confirmationTime = null,
        transactionType = UtxoConsolidation
      )

      bumpabilityChecker.isBumpable(
        transaction = transaction,
        walletUnspentOutputs = immutableListOf(
          BdkUtxo(
            outPoint = BdkOutPoint("abc", 0u),
            txOut = BdkTxOutMock,
            isSpent = false
          )
        )
      ).shouldBeFalse()
    }

    test("bumpable if a utxo consolidation, <=1 wallet utxos, and allow_shrinking enabled") {
      feeBumpAllowShrinkingChecker.shrinkingOutput = BdkScriptMock()
      val transaction = BitcoinTransactionMock(
        total = BitcoinMoney.sats(100),
        inputs = immutableListOf(),
        outputs = immutableListOf(
          BdkTxOut(value = 100u, scriptPubkey = BdkScriptMock())
        ),
        confirmationTime = null,
        transactionType = UtxoConsolidation
      )

      bumpabilityChecker.isBumpable(
        transaction = transaction,
        walletUnspentOutputs = immutableListOf(
          BdkUtxo(
            outPoint = BdkOutPoint("abc", 0u),
            txOut = BdkTxOutMock,
            isSpent = false
          )
        )
      ).shouldBeTrue()
    }

    test("bumpable if a utxo consolidation and >1 wallet utxos") {
      feeBumpAllowShrinkingChecker.shrinkingOutput = BdkScriptMock()
      val transaction = BitcoinTransactionMock(
        total = BitcoinMoney.sats(100),
        inputs = immutableListOf(),
        outputs = immutableListOf(
          BdkTxOut(value = 100u, scriptPubkey = BdkScriptMock())
        ),
        confirmationTime = null,
        transactionType = UtxoConsolidation
      )

      bumpabilityChecker.isBumpable(
        transaction = transaction,
        walletUnspentOutputs = immutableListOf(
          BdkUtxo(
            outPoint = BdkOutPoint("abc", 0u),
            txOut = BdkTxOutMock,
            isSpent = false
          ),
          BdkUtxo(
            outPoint = BdkOutPoint("def", 0u),
            txOut = BdkTxOutMock,
            isSpent = false
          )
        )
      ).shouldBeTrue()
    }
  }
})
