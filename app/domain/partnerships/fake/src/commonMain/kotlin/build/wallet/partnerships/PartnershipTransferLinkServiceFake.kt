package build.wallet.partnerships

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class PartnershipTransferLinkServiceFake : PartnershipTransferLinkService {
  val defaultPartnerInfo = PartnerInfo(
    logoUrl = "https://partner.example.com/logo.png",
    logoBadgedUrl = "https://partner.example.com/logo-badged.png",
    name = "Test Partner",
    partnerId = PartnerId("test-partner")
  )

  private val redirectInfo = TransferLinkRedirectInfo(
    redirectMethod = PartnerRedirectionMethod.Web(
      urlString = "https://partner.example.com/transfer/abc",
      partnerInfo = defaultPartnerInfo
    ),
    partnerName = "Test Partner"
  )

  var getPartnerInfoForPartnerResult: Result<PartnerInfo, GetPartnerInfoError> =
    Ok(defaultPartnerInfo)
  var processTransferLinkResult: Result<TransferLinkRedirectInfo, ProcessTransferLinkError> =
    Ok(redirectInfo)

  override suspend fun getPartnerInfoForPartner(
    partner: String,
  ): Result<PartnerInfo, GetPartnerInfoError> = getPartnerInfoForPartnerResult

  override suspend fun processTransferLink(
    partnerInfo: PartnerInfo,
    tokenizedSecret: String,
  ): Result<TransferLinkRedirectInfo, ProcessTransferLinkError> = processTransferLinkResult

  fun reset() {
    getPartnerInfoForPartnerResult = Ok(defaultPartnerInfo)
    processTransferLinkResult = Ok(redirectInfo)
  }
}
