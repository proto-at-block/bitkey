package bitkey.f8e.account

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.cloud.backup.csek.SealedSsek
import build.wallet.encrypt.XCiphertext
import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.NetworkingError
import com.github.michaelbull.result.Result
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

interface WalletMetadataBackupF8eClient {
  suspend fun get(
    f8eEnvironment: F8eEnvironment,
    accountId: FullAccountId,
  ): Result<WalletMetadataBackup?, NetworkingError>

  suspend fun put(
    f8eEnvironment: F8eEnvironment,
    accountId: FullAccountId,
    backup: WalletMetadataBackup,
  ): Result<Unit, NetworkingError>
}

/**
 * Server backup writes are last-write-wins: the sealed snapshot carries its own schema version
 * internally, and clients are responsible for merging concurrent edits when they pull the
 * latest backup.
 */
@Serializable
data class WalletMetadataBackup(
  @SerialName("wrapped_ssek")
  val sealedSsek: SealedSsek,
  @SerialName("sealed_wallet_metadata_snapshot")
  val sealedWalletMetadataSnapshot: XCiphertext,
)
