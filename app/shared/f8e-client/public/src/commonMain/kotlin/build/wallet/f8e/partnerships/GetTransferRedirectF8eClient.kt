package build.wallet.f8e.partnerships

import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.NetworkingError
import build.wallet.partnerships.PartnershipTransactionId
import com.github.michaelbull.result.Result

interface GetTransferRedirectF8eClient {
  suspend fun getTransferRedirect(
    fullAccountId: FullAccountId,
    address: BitcoinAddress,
    f8eEnvironment: F8eEnvironment,
    partner: String,
    partnerTransactionId: PartnershipTransactionId?,
  ): Result<Success, NetworkingError>

  /**
   * A struct representing the redirect information needed to present the partner experience
   *
   * @property redirectInfo struct including the URL and its type
   */
  data class Success(val redirectInfo: RedirectInfo)
}
