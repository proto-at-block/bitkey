package build.wallet.bitcoin.export

import build.wallet.bitcoin.export.ExportTransactionRow.ExportTransactionType.*
import build.wallet.bitcoin.transactions.BitcoinTransactionId
import build.wallet.money.BitcoinMoney.Companion.btc
import build.wallet.testing.shouldBeOk
import build.wallet.time.someInstant
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class ExportTransactionsAsCsvSerializerImplTests : FunSpec({
  val csvSerializer = ExportTransactionsAsCsvSerializerImpl()

  val outgoingTransaction = ExportTransactionRow(
    txid = BitcoinTransactionId(value = "abc"),
    confirmationTime = someInstant,
    amount = btc(1.0),
    fees = btc(0.001),
    transactionType = Outgoing,
    note = """
      Coffee, bagels, and "receipts"
      Sunday
    """.trimIndent()
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

  test("neutralizes spreadsheet formulas in notes and round-trips to the original note") {
    // Includes notes that already start with an apostrophe: they gain an extra guard
    // apostrophe on export so re-import restores the customer's original note.
    listOf("=1+1", "+1+1", "-1+1", "@SUM(1,1)", "\t=1+1", "'=SUM(1,1)", "''quoted").forEach { note ->
      val dataString = csvSerializer.toCsvString(
        rows = listOf(outgoingTransaction.copy(note = note))
      )

      // The exported Note field is guarded with a leading apostrophe.
      dataString.lines().last().shouldContain("'$note")

      // Re-importing strips the guard, restoring the original note.
      csvSerializer
        .fromCsvString(dataString)
        .shouldBeOk()
        .single()
        .note
        .shouldBe(note)
    }
  }

  test("does not neutralize formula-trigger characters in non-note fields") {
    val negativeAmountTransaction = outgoingTransaction.copy(
      amount = btc("-0.001".toBigDecimal()),
      note = null
    )
    val dataString = csvSerializer.toCsvString(rows = listOf(negativeAmountTransaction))

    // The amount field is written without a formula guard.
    dataString.lines().last().shouldContain(",-0.001,")

    // And it round-trips back to the original negative amount.
    csvSerializer
      .fromCsvString(dataString)
      .shouldBeOk()
      .single()
      .shouldBe(negativeAmountTransaction)
  }
})
