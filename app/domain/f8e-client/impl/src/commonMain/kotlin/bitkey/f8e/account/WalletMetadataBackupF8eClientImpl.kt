package bitkey.f8e.account

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.f8e.client.plugins.withAccountId
import build.wallet.f8e.client.plugins.withEnvironment
import build.wallet.f8e.logging.withDescription
import build.wallet.ktor.result.EmptyResponseBody
import build.wallet.ktor.result.NetworkingError
import build.wallet.ktor.result.RedactedRequestBody
import build.wallet.ktor.result.RedactedResponseBody
import build.wallet.ktor.result.bodyResult
import build.wallet.ktor.result.setRedactedBody
import build.wallet.mapUnit
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import io.ktor.client.request.get
import io.ktor.client.request.put
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@BitkeyInject(AppScope::class)
class WalletMetadataBackupF8eClientImpl(
  private val f8eHttpClient: F8eHttpClient,
) : WalletMetadataBackupF8eClient {
  override suspend fun get(
    f8eEnvironment: F8eEnvironment,
    accountId: FullAccountId,
  ): Result<WalletMetadataBackup?, NetworkingError> =
    f8eHttpClient.authenticated()
      .bodyResult<GetWalletMetadataBackupResponseBody> {
        get("/api/accounts/${accountId.serverId}/client-backup/metadata") {
          withDescription("Get wallet metadata backup from f8e")
          withEnvironment(f8eEnvironment)
          withAccountId(accountId)
        }
      }
      .map { it.walletMetadataBackup }

  override suspend fun put(
    f8eEnvironment: F8eEnvironment,
    accountId: FullAccountId,
    backup: WalletMetadataBackup,
  ): Result<Unit, NetworkingError> =
    f8eHttpClient.authenticated()
      .bodyResult<EmptyResponseBody> {
        put("/api/accounts/${accountId.serverId}/client-backup/metadata") {
          withDescription("Put wallet metadata backup on f8e")
          withEnvironment(f8eEnvironment)
          withAccountId(accountId)
          setRedactedBody(WalletMetadataBackupRequestBody(backup))
        }
      }
      .mapUnit()
}

@Serializable
internal data class GetWalletMetadataBackupResponseBody(
  @SerialName("wallet_metadata_backup")
  val walletMetadataBackup: WalletMetadataBackup?,
) : RedactedResponseBody

@Serializable
internal data class WalletMetadataBackupRequestBody(
  @SerialName("wrapped_ssek")
  val sealedSsek: build.wallet.cloud.backup.csek.SealedSsek,
  @SerialName("sealed_wallet_metadata_snapshot")
  val sealedWalletMetadataSnapshot: build.wallet.encrypt.XCiphertext,
) : RedactedRequestBody {
  constructor(backup: WalletMetadataBackup) : this(
    sealedSsek = backup.sealedSsek,
    sealedWalletMetadataSnapshot = backup.sealedWalletMetadataSnapshot
  )
}
