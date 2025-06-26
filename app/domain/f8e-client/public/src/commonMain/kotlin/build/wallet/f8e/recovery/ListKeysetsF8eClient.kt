package build.wallet.f8e.recovery

import bitkey.backup.DescriptorBackup
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.NetworkingError
import com.github.michaelbull.result.Result

interface ListKeysetsF8eClient {
  suspend fun listKeysets(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
  ): Result<ListKeysetsResponse, NetworkingError>

  /**
   * Includes account's keysets and encrypted descriptor backups.
   */
  data class ListKeysetsResponse(
    val keysets: List<SpendingKeyset>,
    val descriptorBackups: List<DescriptorBackup>?,
  )
}
