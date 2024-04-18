package build.wallet.f8e.partnerships

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.NetworkingError
import build.wallet.partnerships.PartnerInfo
import com.github.michaelbull.result.Result

interface GetTransferPartnerListService {
  suspend fun getTransferPartners(
    fullAccountId: FullAccountId,
    f8eEnvironment: F8eEnvironment,
  ): Result<Success, NetworkingError>

  /**
   * A struct containing a list of partners that offer a transfer experience
   */
  data class Success(val partnerList: List<PartnerInfo>)
}
