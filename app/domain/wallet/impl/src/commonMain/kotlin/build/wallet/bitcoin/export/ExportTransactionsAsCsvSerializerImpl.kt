package build.wallet.bitcoin.export

import build.wallet.bitcoin.transactions.BitcoinTransactionId
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.money.BitcoinMoney
import build.wallet.money.BitcoinMoney.Companion.btc
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.DateTimeArithmeticException
import kotlinx.datetime.Instant

@BitkeyInject(AppScope::class)
class ExportTransactionsAsCsvSerializerImpl : ExportTransactionsAsCsvSerializer {
  override suspend fun toCsvString(rows: List<ExportTransactionRow>): String {
    return withContext(Dispatchers.Default) {
      buildString {
        // Add newline only if row is not empty.
        append(csvHeaderString())
        if (rows.isNotEmpty()) {
          appendLine()
        }

        rows.forEachIndexed { index, row ->
          append(row.toCsvRowString())
          // Add a newline only if it's not the last row
          if (index != rows.lastIndex) {
            appendLine()
          }
        }
      }
    }
  }

  override suspend fun fromCsvString(value: String): Result<List<ExportTransactionRow>, Throwable> {
    return withContext(Dispatchers.Default) {
      val lines = value.lines()
      if (lines.isEmpty()) {
        return@withContext Ok(emptyList())
      }

      val header = lines.first()
      val expectedHeader = csvHeaderString()
      if (header != expectedHeader) {
        return@withContext Err(Error("CSV header does not match expected header"))
      }

      val dataLines = lines.drop(1)
      val rowsResult = dataLines.map { line ->
        parseCsvRow(line)
      }

      // Check for any errors
      val errors = rowsResult.filter { it.isErr }
      if (errors.isNotEmpty()) {
        // Collect error messages
        val errorMessages =
          errors.joinToString(separator = "\n") { it.error.message ?: "Unknown error" }
        return@withContext Err(Error("Errors parsing CSV:\n$errorMessages"))
      }

      val rows = rowsResult
        .filter { it.isOk }
        .map { it.value }

      Ok(rows)
    }
  }

  private fun csvHeaderString(): String {
    return listOf(
      "Transaction ID",
      "Confirmation Time",
      "Amount",
      "Currency",
      "Fee Amount",
      "Fee Currency",
      "Transaction Type"
    ).joinToString(separator = ",")
  }

  private fun ExportTransactionRow.toCsvRowString(): String {
    val amountString = amount.value.toStringExpanded()
    val amountCurrencyString = amount.currency.textCode.code

    val feesString = fees?.value?.toStringExpanded().orEmpty()
    val feesCurrencyString = fees?.currency?.textCode?.code.orEmpty()

    return listOf(
      txid.value,
      confirmationTime.toString(),
      amountString,
      amountCurrencyString,
      feesString,
      feesCurrencyString,
      transactionType
    ).joinToString(separator = ",")
  }

  private fun parseCsvRow(string: String): Result<ExportTransactionRow, Error> {
    val headerFields = csvHeaderString().split(",").map { it.trim() }
    val expectedFieldCount = headerFields.size

    val fields = string.split(",").map { it.trim() }
    if (fields.size != expectedFieldCount) {
      return Err(Error("Invalid CSV row: Expected $expectedFieldCount fields but found ${fields.size}"))
    }

    val fieldMap = headerFields.zip(fields).toMap()

    val txid = fieldMap["Transaction ID"] ?: return Err(Error("Missing 'Transaction ID' field"))

    val confirmationTimeString =
      fieldMap["Confirmation Time"] ?: return Err(Error("Missing 'Confirmation Time' field"))
    val confirmationTime = try {
      Instant.parse(confirmationTimeString)
    } catch (e: DateTimeArithmeticException) {
      return Err(Error("Invalid 'datetime' field: ${e.message}"))
    }

    val amountString = fieldMap["Amount"] ?: return Err(Error("Missing 'Amount' field"))

    val amountValue = try {
      amountString.toBigDecimal()
    } catch (e: NumberFormatException) {
      return Err(Error("Invalid 'Amount' value: ${e.message}"))
    }
    val amount = btc(amountValue)

    val feesString = fieldMap["Fee Amount"].orEmpty()
    val fees: BitcoinMoney? = if (feesString.isNotBlank()) {
      val feesValue = try {
        feesString.toBigDecimal()
      } catch (e: NumberFormatException) {
        return Err(Error("Invalid 'fees' value: ${e.message}"))
      }
      btc(feesValue)
    } else {
      null
    }

    val transactionTypeString =
      fieldMap["Transaction Type"] ?: return Err(Error("Missing 'Transaction Type' field"))
    val transactionType = when (transactionTypeString) {
      INCOMING_TRANSACTION_TYPE_STRING -> ExportTransactionRow.ExportTransactionType.Incoming
      OUTGOING_TRANSACTION_TYPE_STRING -> ExportTransactionRow.ExportTransactionType.Outgoing
      UTXO_CONSOLIDATION_TRANSACTION_TYPE_STRING -> ExportTransactionRow.ExportTransactionType.UtxoConsolidation
      SWEEP_TRANSACTION_TYPE_STRING -> ExportTransactionRow.ExportTransactionType.Sweep
      else -> return Err(Error("Invalid transaction type: $transactionTypeString"))
    }

    return Ok(
      ExportTransactionRow(
        txid = BitcoinTransactionId(value = txid),
        confirmationTime = confirmationTime,
        amount = amount,
        fees = fees,
        transactionType = transactionType
      )
    )
  }
}
