package build.wallet.f8e.partnerships

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.f8e.logging.withDescription
import build.wallet.ktor.result.NetworkingError
import build.wallet.ktor.result.RedactedResponseBody
import build.wallet.ktor.result.bodyResult
import build.wallet.partnerships.PartnerId
import build.wallet.partnerships.PartnerInfo
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import io.ktor.client.request.get
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class GetPartnerF8eClientImpl(
  private val client: F8eHttpClient,
) : GetPartnerF8eClient {
  override suspend fun getPartner(
    fullAccountId: FullAccountId,
    f8eEnvironment: F8eEnvironment,
    partner: PartnerId,
  ): Result<PartnerInfo, NetworkingError> {
    return client
      .authenticated(
        accountId = fullAccountId,
        f8eEnvironment = f8eEnvironment
      )
      .bodyResult<PartnerResponse> {
        get("/api/partnerships/partners/${partner.value}") {
          withDescription("Get partner")
        }
      }
      .map {
        it.toPartnerInfo()
      }
  }

  @Serializable
  data class PartnerResponse(
    @SerialName("logo_url")
    val logoUrl: String?,
    val name: String,
    val partner: PartnerId,
  ) : RedactedResponseBody {
    fun toPartnerInfo(): PartnerInfo {
      return PartnerInfo(logoUrl, name, partner)
    }
  }
}
