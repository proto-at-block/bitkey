package build.wallet.cloud.backup.v2

import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.app.AppRecoveryAuthKey
import build.wallet.bitkey.app.AppSpendingPrivateKey
import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.bitkey.keys.app.AppKey
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.cloud.backup.csek.SealedCsek
import build.wallet.encrypt.SealedData
import build.wallet.encrypt.XCiphertext
import dev.zacsweers.redacted.annotations.Redacted
import kotlinx.serialization.Serializable

/**
 * Fields specific to a full account.
 *
 * Contains hardware-encrypted private material (spending and auth keys).
 *
 * Also contains trusted contact information for Social Recovery, however the
 * instance of this backup does not yet contain a Recovery authentication key,
 * thus it's not possible to perform Social Recovery using this backup.
 */
@Serializable
@Redacted
data class FullAccountFields(
  /** Private key encryption key encrypted by Bitkey hardware's encryption key. */
  val sealedHwEncryptionKey: SealedCsek,
  /**
   * TC RelationshipId -> socRecEncryptionKeyCiphertext.
   * Contains data required for SocRec recovery.
   */
  override val socRecSealedDekMap: Map<String, XCiphertext>,
  val isFakeHardware: Boolean,
  /** Encrypted [FullAccountKeys] using hardware */
  val hwFullAccountKeysCiphertext: SealedData,
  /** Encrypted [FullAccountKeys] using Social Recovery */
  override val socRecSealedFullAccountKeys: XCiphertext,
  /**
   * TODO(BKR-993): Back up rotation auth recovery keys before initiating rotation.
   *
   * An auth key rotation was initiated but not yet completed. This and the
   * [FullAccountKeys.rotationAppGlobalAuthKeypair] are the keys that will be rotated to, but
   * their presence here indicates the process was initiated but not confirmed successful.
   */
  @Serializable(with = AppKeyKeyPairSerializer::class)
  val rotationAppRecoveryAuthKeypair: AppKey<AppRecoveryAuthKey>?,
  val appGlobalAuthKeyHwSignature: AppGlobalAuthKeyHwSignature,
) : SocRecV1AccountFeatures

/**
 * Key info for a full customer, encrypted in [FullAccountFields.hwFullAccountKeysCiphertext]
 * and [FullAccountFields.socRecSealedFullAccountKeys].
 */
@Serializable
@Redacted
data class FullAccountKeys(
  @Serializable(with = SpendingKeysetSerializer::class)
  val activeSpendingKeyset: SpendingKeyset,
  @Serializable(with = AppKeyKeyPairSerializer::class)
  val appGlobalAuthKeypair: AppKey<AppGlobalAuthKey>,
  val appSpendingKeys: Map<
    AppSpendingPublicKey,
    AppSpendingPrivateKey
  >,
  @Serializable(with = HwSpendingPublicKeySerializer::class)
  val activeHwSpendingKey: HwSpendingPublicKey,
  @Serializable(with = HwAuthPublicKeySerializer::class)
  val activeHwAuthKey: HwAuthPublicKey,
  /**
   * TODO(BKR-993): Back up rotation auth recovery keys before initiating rotation.
   *
   * An auth key rotation was initiated but not yet completed. This and the
   * [FullAccountFields.rotationAppRecoveryAuthKeypair] are the keys that will be rotated to, but
   * their presence here indicates the process was initiated but not confirmed successful.
   */
  @Serializable(with = AppKeyKeyPairSerializer::class)
  val rotationAppGlobalAuthKeypair: AppKey<AppGlobalAuthKey>?,
)
