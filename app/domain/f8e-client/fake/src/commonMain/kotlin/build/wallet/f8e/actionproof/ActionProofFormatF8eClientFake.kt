package build.wallet.f8e.actionproof

import build.wallet.bitkey.f8e.AccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.NetworkingError
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class ActionProofFormatF8eClientFake : ActionProofFormatF8eClient {
  var formatValueResult: Result<String, NetworkingError> = Ok("50.00 USD")

  override suspend fun formatValue(
    f8eEnvironment: F8eEnvironment,
    accountId: AccountId,
    request: FormatValueRequest,
  ): Result<String, NetworkingError> {
    return formatValueResult
  }

  fun reset() {
    formatValueResult = Ok("50.00 USD")
  }
}
