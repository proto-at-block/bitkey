package build.wallet.cloud.backup

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitkey.app.AppRecoveryAuthKey
import build.wallet.bitkey.keys.app.AppKey
import build.wallet.bitkey.relationships.DelegatedDecryptionKey
import build.wallet.cloud.backup.v2.AppKeyKeyPairSerializer
import build.wallet.cloud.backup.v2.FullAccountFields
import build.wallet.f8e.F8eEnvironment
import dev.zacsweers.redacted.annotations.Redacted
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Represents a backup for Lite or Full account. Full account data is represented by [FullAccountFields].
 *
 * Adds `deviceNickname` and `createdAt` fields to CloudBackupV2.
 *
 * **IMPORTANT**: Never modify this class - it must remain immutable for backward compatibility.
 * @see [CloudBackup] for migration rules.
 */
@Redacted
@Serializable
data class CloudBackupV3(
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
  val deviceNickname: String?,
  val createdAt: Instant,
) : CloudBackup, SocRecV1BackupFeatures
