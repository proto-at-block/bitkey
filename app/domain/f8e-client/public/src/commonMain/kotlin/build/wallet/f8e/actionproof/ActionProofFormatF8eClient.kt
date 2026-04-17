package build.wallet.f8e.actionproof

import build.wallet.bitkey.f8e.AccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.NetworkingError
import com.github.michaelbull.result.Result

/**
 * Client for the server's format-value endpoint.
 * Returns a locale-aware formatted string for action proof display values.
 */
interface ActionProofFormatF8eClient {
  suspend fun formatValue(
    f8eEnvironment: F8eEnvironment,
    accountId: AccountId,
    request: FormatValueRequest,
  ): Result<String, NetworkingError>
}
