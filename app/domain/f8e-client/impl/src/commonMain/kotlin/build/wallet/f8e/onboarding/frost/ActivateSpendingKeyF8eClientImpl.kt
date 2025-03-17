package build.wallet.f8e.onboarding.frost

import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.f8e.SoftwareAccountId
import build.wallet.bitkey.f8e.SoftwareKeyDefinitionId
import build.wallet.crypto.PublicKey
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.f8e.client.plugins.withAccountId
import build.wallet.f8e.client.plugins.withAppAuthKey
import build.wallet.f8e.client.plugins.withEnvironment
import build.wallet.f8e.logging.withDescription
import build.wallet.ktor.result.*
import build.wallet.mapUnit
import com.github.michaelbull.result.Result
import io.ktor.client.request.put
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@BitkeyInject(AppScope::class)
class ActivateSpendingKeyF8eClientImpl(
  private val f8eHttpClient: F8eHttpClient,
) : ActivateSpendingDescriptorF8eClient {
  override suspend fun activateSpendingKey(
    f8eEnvironment: F8eEnvironment,
    accountId: SoftwareAccountId,
    appAuthKey: PublicKey<AppGlobalAuthKey>,
    softwareKeyDefinitionId: SoftwareKeyDefinitionId,
  ): Result<Unit, NetworkingError> =
    f8eHttpClient
      .authenticated()
      .bodyResult<EmptyResponseBody> {
        put(urlString = "/api/accounts/${accountId.serverId}/spending-key-definition") {
          withDescription("Activate spending key")
          withEnvironment(f8eEnvironment)
          withAccountId(accountId)
          withAppAuthKey(appAuthKey)
          setRedactedBody(RequestBody(keyDefinitionId = softwareKeyDefinitionId.value))
        }
      }.mapUnit()

  @Serializable
  private data class RequestBody(
    @SerialName("key_definition_id")
    val keyDefinitionId: String,
  ) : RedactedRequestBody
}
