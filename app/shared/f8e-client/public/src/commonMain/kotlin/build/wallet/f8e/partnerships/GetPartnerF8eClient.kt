package build.wallet.f8e.partnerships

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.NetworkingError
import build.wallet.partnerships.PartnerId
import build.wallet.partnerships.PartnerInfo
import com.github.michaelbull.result.Result

/**
 * Used to fetch information about a partner.
 */
interface GetPartnerF8eClient {
  suspend fun getPartner(
    fullAccountId: FullAccountId,
    f8eEnvironment: F8eEnvironment,
    partner: PartnerId,
  ): Result<PartnerInfo, NetworkingError>
}
