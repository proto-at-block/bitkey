package build.wallet.bitcoin.transactions

import build.wallet.bdk.bindings.*
import build.wallet.bitcoin.bdk.BdkWalletMock
import build.wallet.bitcoin.transactions.BitcoinTransaction.TransactionType.Outgoing
import build.wallet.bitcoin.transactions.FeeBumpAllowShrinkingChecker.AllowShrinkingError.*
import build.wallet.compose.collections.emptyImmutableList
import build.wallet.compose.collections.immutableListOf
import build.wallet.coroutines.turbine.turbines
import build.wallet.money.BitcoinMoney
import build.wallet.testing.shouldBeErr
import build.wallet.testing.shouldBeOk
import build.wallet.toUByteList
import com.ionspin.kotlin.bignum.integer.BigInteger
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import okio.ByteString.Companion.encodeUtf8

class FeeBumpAllowShrinkingCheckerImplTests : FunSpec({
  val feeBumpAllowShrinkingChecker = FeeBumpAllowShrinkingCheckerImpl()

  val transaction = BitcoinTransactionMock(
    txid = "my-txid",
    total = BitcoinMoney.sats(100),
    inputs = immutableListOf(),
    outputs = immutableListOf(
      // One output + no walletUnspentOutputs == a sweep
      BdkTxOut(value = 100u, scriptPubkey = BdkScriptMock())
    ),
    confirmationTime = null,
    transactionType = Outgoing
  )

  val wallet = BdkWalletMock(turbines::create)

  beforeTest {
    wallet.reset()
  }

  test("transactionSupportsAllowShrinking returns true is allowShrinkingOutput is not null") {
    val allowShrinking = feeBumpAllowShrinkingChecker.transactionSupportsAllowShrinking(
      transaction = transaction,
      walletUnspentOutputs = emptyImmutableList()
    )

    allowShrinking.shouldBeTrue()
  }

  test("allowShrinkingOutput returns null if more than 1 wallet unspent output") {
    val allowShrinkingScript = feeBumpAllowShrinkingChecker.allowShrinkingOutputScript(
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
    )
    allowShrinkingScript.shouldBeNull()
  }

  test("allowShrinkingOutput returns null if transaction has no outputs") {
    val transactionWithNoOutputs = BitcoinTransactionMock(
      total = BitcoinMoney.sats(100),
      inputs = immutableListOf(),
      outputs = immutableListOf(),
      confirmationTime = null,
      transactionType = Outgoing
    )

    val allowShrinkingScript = feeBumpAllowShrinkingChecker.allowShrinkingOutputScript(
      transaction = transactionWithNoOutputs,
      walletUnspentOutputs = emptyImmutableList()
    )
    allowShrinkingScript.shouldBeNull()
  }

  test("allowShrinkingOutput returns null if transaction has two outputs") {
    val transactionWithTwoOutputs = BitcoinTransactionMock(
      total = BitcoinMoney.sats(100),
      inputs = immutableListOf(),
      outputs = immutableListOf(
        BdkTxOut(value = 100u, scriptPubkey = BdkScriptMock()),
        BdkTxOut(value = 100u, scriptPubkey = BdkScriptMock())
      ),
      confirmationTime = null,
      transactionType = Outgoing
    )

    val allowShrinkingScript = feeBumpAllowShrinkingChecker.allowShrinkingOutputScript(
      transaction = transactionWithTwoOutputs,
      walletUnspentOutputs = emptyImmutableList()
    )
    allowShrinkingScript.shouldBeNull()
  }

  test("allowShrinkingOutput returns output script if criteria pass") {
    val allowShrinkingScript = feeBumpAllowShrinkingChecker.allowShrinkingOutputScript(
      transaction = transaction,
      walletUnspentOutputs = emptyImmutableList()
    )
    allowShrinkingScript.shouldNotBeNull().shouldBe(BdkScriptMock())
  }

  test("allowShrinkingOutput returns null if wallet unspent output does not equal transaction output") {
    val allowShrinkingScript = feeBumpAllowShrinkingChecker.allowShrinkingOutputScript(
      transaction = transaction,
      walletUnspentOutputs = immutableListOf(
        BdkUtxo(
          outPoint = BdkOutPoint("not-my-txid", 0u),
          txOut = BdkTxOutMock,
          isSpent = false
        )
      )
    )
    allowShrinkingScript.shouldBeNull()
  }

  test("allowShrinkingOutput returns script if wallet unspent output equals transaction output") {
    val allowShrinkingScript = feeBumpAllowShrinkingChecker.allowShrinkingOutputScript(
      transaction = transaction,
      walletUnspentOutputs = immutableListOf(
        BdkUtxo(
          outPoint = BdkOutPoint("my-txid", 0u),
          txOut = BdkTxOutMock,
          isSpent = false
        )
      )
    )
    allowShrinkingScript.shouldNotBeNull().shouldBe(BdkScriptMock())
  }

  test("allowShrinkingOutput successfully looks up matching transaction from wallet and returns script") {
    val script = BdkScriptMock("blah".encodeUtf8().toUByteList())
    val output = BdkTxOut(
      value = 1u,
      scriptPubkey = script
    )
    val bdkTransactionDetails = BdkTransactionDetails(
      transaction = BdkTransactionMock(output = listOf(output)),
      fee = BigInteger.ZERO,
      received = BigInteger.ZERO,
      sent = BigInteger.ZERO,
      txid = "my-txid",
      confirmationTime = null
    )

    wallet.listTransactionsResult = BdkResult.Ok(listOf(bdkTransactionDetails))
    wallet.listUnspentBlockingResult = BdkResult.Ok(listOf())

    val allowShrinkingScript = feeBumpAllowShrinkingChecker.allowShrinkingOutputScript(
      txid = "my-txid",
      bdkWallet = wallet
    )

    allowShrinkingScript.shouldNotBeNull().shouldBeOk(script)
  }

  test("allowShrinkingOutput no matching transaction in wallet") {
    val script = BdkScriptMock("blah".encodeUtf8().toUByteList())
    val output = BdkTxOut(
      value = 1u,
      scriptPubkey = script
    )
    val bdkTransactionDetails = BdkTransactionDetails(
      transaction = BdkTransactionMock(output = listOf(output)),
      fee = BigInteger.ZERO,
      received = BigInteger.ZERO,
      sent = BigInteger.ZERO,
      txid = "my-txid",
      confirmationTime = null
    )

    wallet.listUnspentBlockingResult = BdkResult.Ok(listOf())
    wallet.listTransactionsResult = BdkResult.Ok(listOf(bdkTransactionDetails))

    val allowShrinkingScript = feeBumpAllowShrinkingChecker.allowShrinkingOutputScript(
      txid = "not-my-txid",
      bdkWallet = wallet
    )

    allowShrinkingScript.shouldBeErr(FailedToFindTransaction())
  }

  test("allowShrinkingOutput listUnspent failure") {
    val error = BdkError.Generic(Throwable(), null)
    wallet.listUnspentBlockingResult = BdkResult.Err(error)

    val allowShrinkingScript = feeBumpAllowShrinkingChecker.allowShrinkingOutputScript(
      txid = "not-my-txid",
      bdkWallet = wallet
    )

    allowShrinkingScript.shouldBeErr(FailedToListUnspentOutputs(cause = error))
  }

  test("allowShrinkingOutput listTransactions failure") {
    val error = BdkError.Generic(Throwable(), null)
    wallet.listUnspentBlockingResult = BdkResult.Ok(listOf())
    wallet.listTransactionsResult = BdkResult.Err(error)

    val allowShrinkingScript = feeBumpAllowShrinkingChecker.allowShrinkingOutputScript(
      txid = "not-my-txid",
      bdkWallet = wallet
    )

    allowShrinkingScript.shouldBeErr(FailedToListTransactions(cause = error))
  }
})
