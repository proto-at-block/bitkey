package build.wallet.f8e.partnerships

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.f8e.logging.withDescription
import build.wallet.f8e.partnerships.GetTransferPartnerListF8eClient.Success
import build.wallet.ktor.result.NetworkingError
import build.wallet.ktor.result.RedactedRequestBody
import build.wallet.ktor.result.RedactedResponseBody
import build.wallet.ktor.result.bodyResult
import build.wallet.ktor.result.setRedactedBody
import build.wallet.partnerships.PartnerInfo
import build.wallet.platform.settings.CountryCodeGuesser
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import io.ktor.client.request.post
import kotlinx.serialization.Serializable

class GetTransferPartnerListF8eClientImpl(
  private val countryCodeGuesser: CountryCodeGuesser,
  private val f8eHttpClient: F8eHttpClient,
) : GetTransferPartnerListF8eClient {
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
          withDescription("Get partnerships transfer partners")
          setRedactedBody(
            RequestBody(
              countryCodeGuesser.countryCode()
            )
          )
        }
      }
      .map { body -> Success(body.partners) }
  }
}

@Serializable
private data class RequestBody(
  val country: String,
) : RedactedRequestBody

@Serializable
private data class ResponseBody(
  val partners: List<PartnerInfo>,
) : RedactedResponseBody
