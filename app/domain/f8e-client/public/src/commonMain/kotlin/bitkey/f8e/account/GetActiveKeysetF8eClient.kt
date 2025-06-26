package bitkey.f8e.account

import bitkey.backup.DescriptorBackup
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.NetworkingError
import com.github.michaelbull.result.Result

interface GetActiveKeysetF8eClient {
  /**
   * Returns account's active keyset and associated encrypted descriptor backup.
   */
  suspend fun get(
    f8eEnvironment: F8eEnvironment,
    accountId: FullAccountId,
  ): Result<GetKeysetsResponse, NetworkingError>

  /**
   * @property keyset account's active keyset.
   * @property descriptorBackup account's encrypted descriptor backup. Returns null
   * if backup has never been uploaded to f8e (for example for older accounts, before
   * descriptor privacy improvements).
   */
  data class GetKeysetsResponse(
    val keyset: SpendingKeyset,
    val descriptorBackup: DescriptorBackup?,
  )
}
