package build.wallet.f8e.recovery

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.NetworkingError
import com.github.michaelbull.result.Result

interface GetAccountStatusService {
  suspend fun status(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    networkType: BitcoinNetworkType,
  ): Result<AccountStatus, NetworkingError>

  data class AccountStatus(
    val sourceServerSpendingKeysetId: String,
    val spendingKeyset: SpendingKeyset,
  )
}
