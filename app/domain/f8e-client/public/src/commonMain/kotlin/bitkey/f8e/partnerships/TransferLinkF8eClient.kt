package bitkey.f8e.partnerships

import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.partnerships.RedirectInfo
import build.wallet.ktor.result.NetworkingError
import build.wallet.partnerships.PartnerInfo
import com.github.michaelbull.result.Result

interface TransferLinkF8eClient {
  /**
   * Returns the list of partners eligible for a transfer link in the user's
   * current country
   */
  suspend fun getTransferPartners(
    fullAccountId: FullAccountId,
    f8eEnvironment: F8eEnvironment,
  ): Result<List<PartnerInfo>, NetworkingError>

  /**
   * This endpoint is used to link Bitkey with external partners (e.g. Strike) by providing a
   * Bitcoin address and verifying the partner's tokenized secret. The response contains a
   * redirect URL for returning the user to the partner application.
   */
  suspend fun getTransferLinkRedirect(
    fullAccountId: FullAccountId,
    f8eEnvironment: F8eEnvironment,
    tokenizedSecret: String,
    partner: String,
    address: BitcoinAddress,
  ): Result<RedirectInfo, NetworkingError>
}
