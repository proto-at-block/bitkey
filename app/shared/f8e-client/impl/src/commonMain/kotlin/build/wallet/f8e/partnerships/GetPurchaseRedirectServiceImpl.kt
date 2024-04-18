package build.wallet.f8e.partnerships

import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.f8e.partnerships.GetPurchaseRedirectService.Success
import build.wallet.ktor.result.NetworkingError
import build.wallet.ktor.result.bodyResult
import build.wallet.money.FiatMoney
import build.wallet.partnerships.PartnershipTransactionId
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class GetPurchaseRedirectServiceImpl(
  private val f8eHttpClient: F8eHttpClient,
) : GetPurchaseRedirectService {
  override suspend fun purchaseRedirect(
    fullAccountId: FullAccountId,
    address: BitcoinAddress,
    f8eEnvironment: F8eEnvironment,
    fiatAmount: FiatMoney,
    partner: String,
    paymentMethod: String,
    quoteId: String?,
    partnerTransactionId: PartnershipTransactionId?,
  ): Result<Success, NetworkingError> {
    return f8eHttpClient
      .authenticated(
        accountId = fullAccountId,
        f8eEnvironment = f8eEnvironment
      )
      .bodyResult<ResponseBody> {
        post("/api/partnerships/purchases/redirects") {
          setBody(
            RequestBody(
              address = address.address,
              fiatAmount = fiatAmount.value.doubleValue(exactRequired = false),
              fiatCurrency = fiatAmount.currency.textCode.code.uppercase(),
              partner = partner,
              paymentMethod = paymentMethod,
              quoteId = quoteId,
              partnerTransactionId = partnerTransactionId
            )
          )
        }
      }
      .map { body ->
        Success(
          body.redirectInfo
        )
      }
  }

  @Serializable
  private data class RequestBody(
    val address: String,
    @SerialName("fiat_amount")
    val fiatAmount: Double,
    @SerialName("fiat_currency")
    val fiatCurrency: String,
    val partner: String,
    @SerialName("payment_method")
    val paymentMethod: String,
    @SerialName("quote_id")
    val quoteId: String?,
    @SerialName("partner_transaction_id")
    val partnerTransactionId: PartnershipTransactionId?,
  )

  @Serializable
  private data class ResponseBody(
    @SerialName("redirect_info")
    val redirectInfo: RedirectInfo,
  )
}
