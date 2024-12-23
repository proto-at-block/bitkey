package build.wallet.f8e.notifications

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.f8e.logging.withDescription
import build.wallet.ktor.result.EmptyResponseBody
import build.wallet.ktor.result.RedactedRequestBody
import build.wallet.ktor.result.bodyResult
import build.wallet.ktor.result.setRedactedBody
import build.wallet.mapUnit
import com.github.michaelbull.result.Result
import io.ktor.client.request.post
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@BitkeyInject(AppScope::class)
class RegisterWatchAddressF8eClientImpl(
  private val f8eHttpClient: F8eHttpClient,
) : RegisterWatchAddressF8eClient {
  override suspend fun register(
    addressAndKeysetIds: List<AddressAndKeysetId>,
    fullAccountId: FullAccountId,
    f8eEnvironment: F8eEnvironment,
  ): Result<Unit, Error> {
    return f8eHttpClient.authenticated(
      f8eEnvironment,
      fullAccountId
    )
      .bodyResult<EmptyResponseBody> {
        post("/api/accounts/${fullAccountId.serverId}/notifications/addresses") {
          withDescription("Registering (${addressAndKeysetIds.size}) watch addresses")
          setRedactedBody(
            RegisterWatchAddressRequest(addressAndKeysetIds)
          )
        }
      }
      .mapUnit()
  }
}

@Serializable
private data class RegisterWatchAddressRequest(
  /** String representation of the addresses */
  @SerialName("addresses")
  val addresses: List<AddressAndKeysetId>,
) : RedactedRequestBody
