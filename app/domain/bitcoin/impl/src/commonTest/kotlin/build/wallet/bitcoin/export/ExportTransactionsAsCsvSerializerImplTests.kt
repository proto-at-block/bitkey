package build.wallet.bitcoin.export

import build.wallet.bitcoin.export.ExportTransactionRow.ExportTransactionType.*
import build.wallet.bitcoin.transactions.BitcoinTransactionId
import build.wallet.money.BitcoinMoney.Companion.btc
import build.wallet.testing.shouldBeOk
import build.wallet.time.someInstant
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual

class ExportTransactionsAsCsvSerializerImplTests : FunSpec({
  val csvSerializer = ExportTransactionsAsCsvSerializerImpl()

  val outgoingTransaction = ExportTransactionRow(
    txid = BitcoinTransactionId(value = "abc"),
    confirmationTime = someInstant,
    amount = btc(1.0),
    fees = btc(0.001),
    transactionType = Outgoing
  )

  val incomingTransaction = ExportTransactionRow(
    txid = BitcoinTransactionId(value = "abc"),
    confirmationTime = someInstant,
    amount = btc(1.0),
    fees = btc(0.001),
    transactionType = Incoming
  )

  val consolidationTransaction = ExportTransactionRow(
    txid = BitcoinTransactionId(value = "abc"),
    confirmationTime = someInstant,
    amount = btc(1.0),
    fees = btc(0.001),
    transactionType = UtxoConsolidation
  )

  test("serialize with no transactions") {
    val list = emptyList<ExportTransactionRow>()
    val dataString = csvSerializer.toCsvString(rows = list)

    val deserializedList = csvSerializer.fromCsvString(value = dataString).shouldBeOk()
    deserializedList.shouldBeEqual(list)
  }

  test("serialize with transactions") {
    val list = listOf(outgoingTransaction, incomingTransaction, consolidationTransaction)
    val dataString = csvSerializer.toCsvString(rows = list)

    val deserializedList = csvSerializer.fromCsvString(value = dataString).shouldBeOk()
    deserializedList.shouldBeEqual(list)
    deserializedList[0].shouldBeEqual(outgoingTransaction)
    deserializedList[1].shouldBeEqual(incomingTransaction)
    deserializedList[2].shouldBeEqual(consolidationTransaction)
  }
})
