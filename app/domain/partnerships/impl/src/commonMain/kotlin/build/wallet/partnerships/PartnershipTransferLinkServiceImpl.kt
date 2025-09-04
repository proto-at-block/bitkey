package build.wallet.partnerships

import bitkey.f8e.partnerships.TransferLinkF8eClient
import build.wallet.account.AccountService
import build.wallet.account.getAccount
import build.wallet.bitcoin.address.BitcoinAddressService
import build.wallet.bitkey.account.FullAccount
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.ensure
import build.wallet.f8e.partnerships.RedirectInfo
import build.wallet.f8e.partnerships.RedirectUrlType
import build.wallet.ktor.result.HttpError
import build.wallet.partnerships.GetPartnerInfoError.NoFullAccountFound
import build.wallet.partnerships.GetPartnerInfoError.PartnerNotFound
import build.wallet.partnerships.ProcessTransferLinkError.NotRetryable
import build.wallet.partnerships.ProcessTransferLinkError.Retryable
import build.wallet.platform.links.AppRestrictions
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.mapError

@BitkeyInject(AppScope::class)
class PartnershipTransferLinkServiceImpl(
  private val transferLinkF8eClient: TransferLinkF8eClient,
  private val bitcoinAddressService: BitcoinAddressService,
  private val accountService: AccountService,
) : PartnershipTransferLinkService {
  override suspend fun getPartnerInfoForPartner(
    partner: String,
  ): Result<PartnerInfo, GetPartnerInfoError> {
    return coroutineBinding {
      val account = accountService.getAccount<FullAccount>()
        .mapError { NoFullAccountFound(it) }
        .bind()

      val partners = transferLinkF8eClient.getTransferPartners(
        fullAccountId = account.accountId,
        f8eEnvironment = account.config.f8eEnvironment
      )
        .mapError { GetPartnerInfoError.NetworkingError(it) }
        .bind()

      val matchingPartner =
        partners.firstOrNull { it.name == partner || it.partnerId.value == partner }

      ensure(matchingPartner != null) {
        PartnerNotFound(Error("Could not find $partner in transfer partners $partners"))
      }
      matchingPartner
    }
  }

  override suspend fun processTransferLink(
    partnerInfo: PartnerInfo,
    tokenizedSecret: String,
  ): Result<TransferLinkRedirectInfo, ProcessTransferLinkError> {
    return coroutineBinding {
      val account = accountService.getAccount<FullAccount>()
        .mapError { NotRetryable(Error("No full account found for transfer link")) }
        .bind()

      val address = bitcoinAddressService.generateAddress()
        .mapError { Retryable(Error("Failed to generate bitcoin address for transfer link")) }
        .bind()

      val redirectInfo = transferLinkF8eClient.getTransferLinkRedirect(
        fullAccountId = account.accountId,
        f8eEnvironment = account.config.f8eEnvironment,
        tokenizedSecret = tokenizedSecret,
        partner = partnerInfo.partnerId.value,
        address = address
      )
        .mapError {
          when (it) {
            is HttpError.NetworkError, is HttpError.ServerError -> Retryable(it)
            else -> NotRetryable(it)
          }
        }
        .bind()

      TransferLinkRedirectInfo(
        redirectMethod = redirectInfo.toPartnerRedirectMethod(partnerInfo),
        partnerName = partnerInfo.name
      )
    }
  }

  private fun RedirectInfo.toPartnerRedirectMethod(
    partnerInfo: PartnerInfo,
  ): PartnerRedirectionMethod {
    return when (redirectType) {
      RedirectUrlType.DEEPLINK ->
        PartnerRedirectionMethod.Deeplink(
          urlString = url,
          appRestrictions = appRestrictions?.let {
            AppRestrictions(
              packageName = it.packageName,
              minVersion = it.minVersion
            )
          },
          partnerName = partnerInfo.name
        )
      RedirectUrlType.WIDGET ->
        PartnerRedirectionMethod.Web(
          urlString = url,
          partnerInfo = PartnerInfo(
            logoUrl = null,
            logoBadgedUrl = null,
            name = partnerInfo.name,
            partnerId = partnerInfo.partnerId
          )
        )
    }
  }
}
