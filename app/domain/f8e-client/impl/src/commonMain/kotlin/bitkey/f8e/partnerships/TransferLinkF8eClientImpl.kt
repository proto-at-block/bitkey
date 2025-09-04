package bitkey.f8e.partnerships

import bitkey.auth.AuthTokenScope
import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.f8e.client.plugins.withAccountId
import build.wallet.f8e.client.plugins.withEnvironment
import build.wallet.f8e.logging.withDescription
import build.wallet.f8e.partnerships.RedirectInfo
import build.wallet.ktor.result.*
import build.wallet.partnerships.PartnerInfo
import build.wallet.platform.settings.CountryCodeGuesser
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import io.ktor.client.request.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@BitkeyInject(AppScope::class)
class TransferLinkF8eClientImpl(
  private val f8eHttpClient: F8eHttpClient,
  private val countryCodeGuesser: CountryCodeGuesser,
) : TransferLinkF8eClient {
  override suspend fun getTransferPartners(
    fullAccountId: FullAccountId,
    f8eEnvironment: F8eEnvironment,
  ): Result<List<PartnerInfo>, NetworkingError> {
    return f8eHttpClient
      .authenticated()
      .bodyResult<TransferLinkPartnersResponse> {
        post("/api/partnerships/transfer-links") {
          withDescription("Get partnerships transfer link partners")
          withEnvironment(f8eEnvironment)
          withAccountId(fullAccountId)
          setRedactedBody(
            TransferLinkPartnersRequest(
              countryCodeGuesser.countryCode()
            )
          )
        }
      }
      .map { body -> body.partners }
  }

  override suspend fun getTransferLinkRedirect(
    fullAccountId: FullAccountId,
    f8eEnvironment: F8eEnvironment,
    tokenizedSecret: String,
    partner: String,
    address: BitcoinAddress,
  ): Result<RedirectInfo, NetworkingError> {
    return f8eHttpClient
      .authenticated()
      .bodyResult<RedirectResponse> {
        post("/api/partnerships/transfer-links/redirects") {
          withEnvironment(f8eEnvironment)
          withAccountId(fullAccountId, AuthTokenScope.Global)
          setRedactedBody(
            RedirectRequest(
              partnerTransactionId = tokenizedSecret,
              partner = partner,
              address = address.address
            )
          )
        }
      }
      .map { body ->
        body.redirectInfo
      }
  }

  @Serializable
  private data class TransferLinkPartnersRequest(
    val country: String,
  ) : RedactedRequestBody

  @Serializable
  private data class TransferLinkPartnersResponse(
    val partners: List<PartnerInfo>,
  ) : RedactedResponseBody

  @Serializable
  private data class RedirectRequest(
    @SerialName("partner_transaction_id")
    val partnerTransactionId: String,
    val partner: String,
    val address: String,
  ) : RedactedRequestBody

  @Serializable
  private data class RedirectResponse(
    @SerialName("redirect_info")
    val redirectInfo: RedirectInfo,
  ) : RedactedResponseBody
}
