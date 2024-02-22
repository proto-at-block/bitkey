package build.wallet.nfc

import app.cash.turbine.Turbine
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import com.github.michaelbull.result.toErrorIfNull

class NfcTransactorMock(
  turbine: (String) -> Turbine<Any>,
) : NfcTransactor {
  val transactCalls = turbine("transact calls")
  var transactResult: Result<Any, NfcException> = Err(NfcException.UnknownError())

  override suspend fun <T> transact(
    parameters: NfcSession.Parameters,
    transaction: TransactionFn<T>,
  ): Result<T, NfcException> {
    transactCalls.add(parameters)
    return transactResult.map { it as? T }
      .toErrorIfNull { NfcException.UnknownError() }
  }

  fun reset() {
    transactResult = Err(NfcException.UnknownError())
  }
}
