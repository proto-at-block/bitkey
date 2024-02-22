package build.wallet.cloud.backup

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitkey.app.AppRecoveryAuthKeypair
import build.wallet.bitkey.socrec.TrustedContactIdentityKey
import build.wallet.cloud.backup.v2.AppRecoveryAuthKeypairSerializer
import build.wallet.cloud.backup.v2.FullAccountFields
import build.wallet.cloud.backup.v2.TrustedContactIdentityKeySerializer
import build.wallet.f8e.F8eEnvironment
import dev.zacsweers.redacted.annotations.Redacted
import kotlinx.serialization.Serializable

/**
 * Represents a backup for Lite or Full account. Full account data is represented by [FullAccountFields].
 *
 * Persists Trusted Contact encrypted keys but Social Recovery with an instance of this
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
  @Serializable(with = TrustedContactIdentityKeySerializer::class)
  override val trustedContactIdentityKeypair: TrustedContactIdentityKey,
  @Serializable(with = AppRecoveryAuthKeypairSerializer::class)
  override val appRecoveryAuthKeypair: AppRecoveryAuthKeypair,
  override val fullAccountFields: FullAccountFields?,
  override val isUsingSocRecFakes: Boolean,
  val bitcoinNetworkType: BitcoinNetworkType,
) : CloudBackup, SocRecV1BackupFeatures
