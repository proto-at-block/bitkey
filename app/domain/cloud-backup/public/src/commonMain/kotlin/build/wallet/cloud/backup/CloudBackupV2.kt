package build.wallet.cloud.backup

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitkey.app.AppRecoveryAuthKey
import build.wallet.bitkey.keys.app.AppKey
import build.wallet.bitkey.relationships.DelegatedDecryptionKey
import build.wallet.cloud.backup.v2.AppKeyKeyPairSerializer
import build.wallet.cloud.backup.v2.FullAccountFields
import build.wallet.f8e.F8eEnvironment
import dev.zacsweers.redacted.annotations.Redacted
import kotlinx.serialization.Serializable

/**
 * Represents a backup for Lite or Full account. Full account data is represented by [FullAccountFields].
 *
 * Persists Recovery Contact encrypted keys but Social Recovery with an instance of this
 * backup is still not possible since the backup doesn't contain a Recovery authentication
 * key yet - wasn't implemented at the time.
 */
@Redacted
@Serializable
data class CloudBackupV2(
  override val accountId: String,
  val f8eEnvironment: F8eEnvironment,
  val isTestAccount: Boolean,
  // Note: It's important we override the serializers here to ensure the private keys are stored.
  @Serializable(with = AppKeyKeyPairSerializer::class)
  override val delegatedDecryptionKeypair: AppKey<DelegatedDecryptionKey>,
  @Serializable(with = AppKeyKeyPairSerializer::class)
  override val appRecoveryAuthKeypair: AppKey<AppRecoveryAuthKey>,
  override val fullAccountFields: FullAccountFields?,
  override val isUsingSocRecFakes: Boolean,
  val bitcoinNetworkType: BitcoinNetworkType,
) : CloudBackup, SocRecV1BackupFeatures
