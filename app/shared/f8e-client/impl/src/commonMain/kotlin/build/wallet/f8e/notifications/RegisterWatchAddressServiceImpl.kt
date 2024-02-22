package build.wallet.f8e.notifications

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.ktor.result.bodyResult
import build.wallet.logging.logNetworkFailure
import build.wallet.mapUnit
import com.github.michaelbull.result.Result
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class RegisterWatchAddressServiceImpl(
  private val f8eHttpClient: F8eHttpClient,
) : RegisterWatchAddressService {
  override suspend fun register(
    addressAndKeysetIds: List<AddressAndKeysetId>,
    fullAccountId: FullAccountId,
    f8eEnvironment: F8eEnvironment,
  ): Result<Unit, Error> {
    return f8eHttpClient.authenticated(
      f8eEnvironment,
      fullAccountId
    )
      .bodyResult<RegisterWatchAddressResponse> {
        post("/api/accounts/${fullAccountId.serverId}/notifications/addresses") {
          setBody(
            RegisterWatchAddressRequest(addressAndKeysetIds)
          )
        }
      }
      .logNetworkFailure { "Error registering watch addresses: $addressAndKeysetIds with server" }
      .mapUnit()
  }
}

@Serializable
private data class RegisterWatchAddressRequest(
  /** String representation of the addresses */
  @SerialName("addresses")
  val addresses: List<AddressAndKeysetId>,
)

@Serializable
private class RegisterWatchAddressResponse
