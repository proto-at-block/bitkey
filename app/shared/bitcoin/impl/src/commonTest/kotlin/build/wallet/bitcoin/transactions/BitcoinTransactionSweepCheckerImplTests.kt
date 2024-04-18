package build.wallet.bitcoin.transactions

import build.wallet.bdk.bindings.BdkOutPoint
import build.wallet.bdk.bindings.BdkScriptMock
import build.wallet.bdk.bindings.BdkTxIn
import build.wallet.bdk.bindings.BdkTxOut
import build.wallet.bdk.bindings.BdkTxOutMock
import build.wallet.bdk.bindings.BdkUtxo
import build.wallet.bitcoin.transactions.BitcoinTransactionSweepChecker.TransactionError.MultipleOutputsError
import build.wallet.bitcoin.transactions.BitcoinTransactionSweepChecker.TransactionError.WalletNotEmptyError
import build.wallet.compose.collections.emptyImmutableList
import build.wallet.compose.collections.immutableListOf
import build.wallet.money.BitcoinMoney
import build.wallet.testing.shouldBeErrOfType
import build.wallet.testing.shouldBeOk
import io.kotest.core.spec.style.FunSpec
import kotlinx.collections.immutable.toImmutableList

class BitcoinTransactionSweepCheckerImplTests : FunSpec({
  val sweepChecker = BitcoinTransactionSweepCheckerImpl()

  test("should return Ok if wallet has no utxos, and transaction has only one output") {
    val sweepOutput = BdkTxOut(value = 100u, scriptPubkey = BdkScriptMock())
    val transaction = BitcoinTransactionMock(
      total = BitcoinMoney.sats(100),
      inputs = immutableListOf(
        BdkTxIn(outpoint = BdkOutPoint("abc", 0u), sequence = 0u, witness = emptyList()),
        BdkTxIn(outpoint = BdkOutPoint("def", 0u), sequence = 0u, witness = emptyList())
      ),
      outputs = immutableListOf(sweepOutput),
      confirmationTime = null
    )

    sweepChecker.sweepOutput(
      transaction = transaction,
      walletUnspentOutputs = emptyImmutableList()
    ).shouldBeOk(sweepOutput)
  }

  test("should return error if wallet has unspent utxos") {
    // Wallet has UTXOs that isn't in the PSBT
    val utxos = listOf(
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

    val transaction = BitcoinTransactionMock(
      total = BitcoinMoney.sats(100),
      inputs = immutableListOf(
        BdkTxIn(outpoint = BdkOutPoint("abc", 0u), sequence = 0u, witness = emptyList())
      ),
      outputs = immutableListOf(BdkTxOut(value = 100u, scriptPubkey = BdkScriptMock())),
      confirmationTime = null
    )

    sweepChecker.sweepOutput(
      transaction = transaction,
      walletUnspentOutputs = utxos.toImmutableList()
    ).shouldBeErrOfType<WalletNotEmptyError>()
  }

  test("should return false if the PSBT has more than one output") {
    val transaction = BitcoinTransactionMock(
      total = BitcoinMoney.sats(300),
      inputs = immutableListOf(),
      outputs = immutableListOf(
        BdkTxOut(value = 100u, scriptPubkey = BdkScriptMock()),
        BdkTxOut(value = 200u, scriptPubkey = BdkScriptMock())
      ),
      confirmationTime = null
    )

    sweepChecker.sweepOutput(
      transaction = transaction,
      walletUnspentOutputs = emptyImmutableList()
    ).shouldBeErrOfType<MultipleOutputsError>()
  }
})
