package build.wallet.f8e.partnerships

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.f8e.partnerships.GetTransferPartnerListService.Success
import build.wallet.ktor.result.NetworkingError
import build.wallet.ktor.result.bodyResult
import build.wallet.logging.logNetworkFailure
import build.wallet.platform.settings.CountryCodeGuesser
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.serialization.Serializable

class GetTransferPartnerListServiceImpl(
  private val countryCodeGuesser: CountryCodeGuesser,
  private val f8eHttpClient: F8eHttpClient,
) : GetTransferPartnerListService {
  override suspend fun getTransferPartners(
    fullAccountId: FullAccountId,
    f8eEnvironment: F8eEnvironment,
  ): Result<Success, NetworkingError> {
    return f8eHttpClient
      .authenticated(
        accountId = fullAccountId,
        f8eEnvironment = f8eEnvironment
      )
      .bodyResult<ResponseBody> {
        post("/api/partnerships/transfers") {
          setBody(
            RequestBody(
              countryCodeGuesser.countryCode()
            )
          )
        }
      }
      .map { body -> Success(body.partners) }
      .logNetworkFailure { "Failed to get partnerships transfer partners" }
  }
}

@Serializable
private data class RequestBody(
  val country: String,
)

@Serializable
private data class ResponseBody(
  val partners: List<PartnerInfo>,
)
