package build.wallet.f8e.onboarding.frost

import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.f8e.SoftwareAccountId
import build.wallet.bitkey.f8e.SoftwareKeysetId
import build.wallet.crypto.PublicKey
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.f8e.logging.withDescription
import build.wallet.ktor.result.*
import build.wallet.mapUnit
import com.github.michaelbull.result.Result
import io.ktor.client.request.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class ActivateSpendingDescriptorF8eClientImpl(
  private val f8eHttpClient: F8eHttpClient,
) : ActivateSpendingDescriptorF8eClient {
  override suspend fun activateSpendingDescriptor(
    f8eEnvironment: F8eEnvironment,
    accountId: SoftwareAccountId,
    appAuthKey: PublicKey<AppGlobalAuthKey>,
    keysetId: SoftwareKeysetId,
  ): Result<Unit, NetworkingError> =
    f8eHttpClient
      .authenticated(
        f8eEnvironment = f8eEnvironment,
        accountId = accountId,
        appFactorProofOfPossessionAuthKey = appAuthKey
      ).bodyResult<EmptyResponseBody> {
        put(urlString = "/api/accounts/${accountId.serverId}/spending-descriptor") {
          withDescription("Activate spending descriptor")
          setRedactedBody(
            RequestBody(
              keysetId = keysetId.keysetId
            )
          )
        }
      }.mapUnit()

  @Serializable
  private data class RequestBody(
    @SerialName("keyset_id")
    val keysetId: String,
  ) : RedactedRequestBody
}
