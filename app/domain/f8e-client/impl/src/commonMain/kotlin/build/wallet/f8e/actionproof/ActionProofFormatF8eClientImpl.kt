package build.wallet.f8e.actionproof

import build.wallet.bitkey.f8e.AccountId
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.f8e.client.plugins.withAccountId
import build.wallet.f8e.client.plugins.withEnvironment
import build.wallet.ktor.result.NetworkingError
import build.wallet.ktor.result.RedactedResponseBody
import build.wallet.ktor.result.bodyResult
import build.wallet.ktor.result.setRedactedBody
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import io.ktor.client.request.post
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@BitkeyInject(AppScope::class)
class ActionProofFormatF8eClientImpl(
  private val f8eHttpClient: F8eHttpClient,
) : ActionProofFormatF8eClient {
  override suspend fun formatValue(
    f8eEnvironment: F8eEnvironment,
    accountId: AccountId,
    request: FormatValueRequest,
  ): Result<String, NetworkingError> {
    return f8eHttpClient.authenticated()
      .bodyResult<FormatValueResponseBody> {
        post("/api/accounts/${accountId.serverId}/action-proof/format-value") {
          withEnvironment(f8eEnvironment)
          withAccountId(accountId)
          setRedactedBody(request)
        }
      }
      .map { it.formattedValue }
  }

  @Serializable
  private data class FormatValueResponseBody(
    @SerialName("formatted_value")
    val formattedValue: String,
  ) : RedactedResponseBody
}
