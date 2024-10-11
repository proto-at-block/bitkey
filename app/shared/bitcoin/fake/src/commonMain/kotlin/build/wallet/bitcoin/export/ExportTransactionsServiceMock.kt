package build.wallet.bitcoin.export

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import okio.ByteString

class ExportTransactionsServiceMock : ExportTransactionsService {
  var result: Result<ExportedTransactions, Throwable> = Ok(ExportedTransactions(ByteString.EMPTY))

  override suspend fun export(): Result<ExportedTransactions, Throwable> {
    return result
  }

  fun reset() {
    result = Ok(ExportedTransactions(ByteString.EMPTY))
  }
}
