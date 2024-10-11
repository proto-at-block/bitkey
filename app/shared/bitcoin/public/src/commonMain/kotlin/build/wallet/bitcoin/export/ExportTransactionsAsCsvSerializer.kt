package build.wallet.bitcoin.export

import com.github.michaelbull.result.Result

interface ExportTransactionsAsCsvSerializer {
  suspend fun toCsvString(rows: List<ExportTransactionRow>): String

  suspend fun fromCsvString(value: String): Result<List<ExportTransactionRow>, Throwable>
}
