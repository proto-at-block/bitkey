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
      val records = value.parseCsvRecords()
      if (records.isEmpty()) {
        return@withContext Ok(emptyList())
      }

      val header = records.first()
      val expectedHeader = csvHeaderFields()
      if (header != expectedHeader) {
        return@withContext Err(Error("CSV header does not match expected header"))
      }

      val dataRecords = records.drop(1)
      val rowsResult = dataRecords.map { record ->
        parseCsvRow(record)
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
    return csvHeaderFields().joinToString(separator = ",") { field -> field.toCsvField() }
  }

  private fun csvHeaderFields(): List<String> {
    return listOf(
      "Transaction ID",
      "Confirmation Time",
      "Amount",
      "Currency",
      "Fee Amount",
      "Fee Currency",
      "Transaction Type",
      "Note"
    )
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
      transactionType.toString()
    ).joinToString(separator = ",") { field -> field.toCsvField() } +
      "," + note.orEmpty().neutralizeSpreadsheetFormula().toCsvField()
  }

  private fun parseCsvRow(fields: List<String>): Result<ExportTransactionRow, Error> {
    val headerFields = csvHeaderFields()
    val expectedFieldCount = headerFields.size

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
        transactionType = transactionType,
        note = fieldMap["Note"].orEmpty()
          .restoreNeutralizedSpreadsheetFormula()
          .takeIf { it.isNotBlank() }
      )
    )
  }
}

private fun String.toCsvField(): String {
  val escaped = replace("\"", "\"\"")
  return if (any(Char::requiresCsvEscaping)) {
    "\"$escaped\""
  } else {
    escaped
  }
}

/**
 * Prepends a `'` guard to fields that would otherwise be interpreted as a formula by
 * spreadsheet applications. Only applied to user-controlled fields (the Note column).
 *
 * Notes that already start with a `'` are also guarded (like Excel's own escaping) so that
 * [restoreNeutralizedSpreadsheetFormula] can always strip exactly one guard character —
 * otherwise a note like `'=SUM(1,1)` would lose its leading apostrophe on re-import.
 */
private fun String.neutralizeSpreadsheetFormula(): String =
  if (firstOrNull()?.requiresSpreadsheetFormulaGuard() == true) {
    "'$this"
  } else {
    this
  }

/**
 * Reverses [neutralizeSpreadsheetFormula] by stripping a single leading `'` when it guards a
 * character that [neutralizeSpreadsheetFormula] would have guarded, so that neutralized notes
 * round-trip to their original value.
 */
private fun String.restoreNeutralizedSpreadsheetFormula(): String =
  if (firstOrNull() == '\'' && getOrNull(1)?.requiresSpreadsheetFormulaGuard() == true) {
    drop(1)
  } else {
    this
  }

private fun Char.requiresSpreadsheetFormulaGuard(): Boolean =
  this == '\'' || isSpreadsheetFormulaTrigger()

private fun Char.isSpreadsheetFormulaTrigger(): Boolean =
  when (this) {
    '=', '+', '-', '@', '\t', '\r' -> true
    else -> false
  }

private fun Char.requiresCsvEscaping(): Boolean =
  when (this) {
    ',', '"', '\n', '\r' -> true
    else -> false
  }

private fun String.parseCsvRecords(): List<List<String>> {
  if (isEmpty()) {
    return emptyList()
  }

  val parser = CsvRecordsParser()
  var index = 0
  while (index < length) {
    index += parser.consume(char = this[index], next = getOrNull(index + 1))
  }
  parser.finish(endsWithComma = lastOrNull() == ',')
  return parser.records
}

/**
 * Incremental RFC-4180-style CSV parser. Feed characters via [consume] and call [finish]
 * once input is exhausted; parsed records accumulate in [records].
 */
private class CsvRecordsParser {
  val records = mutableListOf<List<String>>()

  private val fields = mutableListOf<String>()
  private val currentField = StringBuilder()
  private var insideQuotes = false

  /**
   * Consumes [char] (with [next] as one character of lookahead) and returns how many
   * characters of input were consumed (1, or 2 for escaped quotes and CRLF).
   */
  fun consume(
    char: Char,
    next: Char?,
  ): Int =
    if (insideQuotes) {
      consumeQuoted(char, next)
    } else {
      consumeUnquoted(char, next)
    }

  fun finish(endsWithComma: Boolean) {
    if (currentField.isNotEmpty() || fields.isNotEmpty() || endsWithComma) {
      finishRecord()
    }
  }

  private fun consumeQuoted(
    char: Char,
    next: Char?,
  ): Int =
    when {
      char == '"' && next == '"' -> {
        currentField.append('"')
        2
      }
      char == '"' -> {
        insideQuotes = false
        1
      }
      else -> {
        currentField.append(char)
        1
      }
    }

  private fun consumeUnquoted(
    char: Char,
    next: Char?,
  ): Int =
    when (char) {
      '"' -> {
        insideQuotes = true
        1
      }
      ',' -> {
        finishField()
        1
      }
      '\n' -> {
        finishRecord()
        1
      }
      '\r' -> {
        finishRecord()
        if (next == '\n') 2 else 1
      }
      else -> {
        currentField.append(char)
        1
      }
    }

  private fun finishField() {
    fields += currentField.toString()
    currentField.clear()
  }

  private fun finishRecord() {
    finishField()
    records += fields.toList()
    fields.clear()
  }
}
