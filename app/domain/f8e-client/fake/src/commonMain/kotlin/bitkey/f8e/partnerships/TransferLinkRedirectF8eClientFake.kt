package bitkey.f8e.partnerships

import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.partnerships.RedirectInfo
import build.wallet.f8e.partnerships.RedirectUrlType.WIDGET
import build.wallet.ktor.result.NetworkingError
import build.wallet.partnerships.PartnerId
import build.wallet.partnerships.PartnerInfo
import build.wallet.partnerships.PartnershipTransactionId
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class TransferLinkRedirectF8eClientFake : TransferLinkF8eClient {
  private val redirectInfo = RedirectInfo(
    appRestrictions = null,
    url = "http://example.com/redirect_url",
    redirectType = WIDGET,
    partnerTransactionId = PartnershipTransactionId("some-partner-transaction-id")
  )

  private val defaultPartners = listOf(
    PartnerInfo(
      logoUrl = "https://example.com/logo1.png",
      logoBadgedUrl = "https://example.com/logo1-badged.png",
      name = "Partner 1",
      partnerId = PartnerId("partner1")
    ),
    PartnerInfo(
      logoUrl = null,
      logoBadgedUrl = null,
      name = "Partner 2",
      partnerId = PartnerId("partner2")
    )
  )

  var getTransferLinkRedirectResult: Result<RedirectInfo, NetworkingError> = Ok(redirectInfo)
  var getTransferPartnersResult: Result<List<PartnerInfo>, NetworkingError> = Ok(defaultPartners)

  override suspend fun getTransferPartners(
    fullAccountId: FullAccountId,
    f8eEnvironment: F8eEnvironment,
  ): Result<List<PartnerInfo>, NetworkingError> = getTransferPartnersResult

  override suspend fun getTransferLinkRedirect(
    fullAccountId: FullAccountId,
    f8eEnvironment: F8eEnvironment,
    tokenizedSecret: String,
    partner: String,
    address: BitcoinAddress,
  ): Result<RedirectInfo, NetworkingError> = getTransferLinkRedirectResult

  fun reset() {
    getTransferLinkRedirectResult = Ok(redirectInfo)
    getTransferPartnersResult = Ok(defaultPartners)
  }
}
