package build.wallet.cloud.backup.v2

import build.wallet.bitkey.app.AppGlobalAuthKeypair
import build.wallet.bitkey.app.AppSpendingPrivateKey
import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.cloud.backup.csek.SealedCsek
import build.wallet.crypto.PublicKey
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
  val hwEncryptionKeyCiphertext: SealedCsek,
  /**
   * TC RelationshipId -> socRecEncryptionKeyCiphertext.
   * Contains data required for SocRec recovery.
   */
  override val socRecEncryptionKeyCiphertextMap: Map<String, XCiphertext>,
  val isFakeHardware: Boolean,
  /** Encrypted [FullAccountKeys] using hardware */
  val hwFullAccountKeysCiphertext: SealedData,
  /** Encrypted [FullAccountKeys] using Social Recovery */
  override val socRecFullAccountKeysCiphertext: XCiphertext,
  /**
   * Identity key for the protected customer role. We only store the public key and only
   * use it for social challenge verification.
   */
  override val protectedCustomerIdentityPublicKey: PublicKey,
) : SocRecV1AccountFeatures

/**
 * Key info for a full customer, encrypted in [FullAccountFields.hwFullAccountKeysCiphertext]
 * and [FullAccountFields.socRecFullAccountKeysCiphertext].
 */
@Serializable
@Redacted
data class FullAccountKeys(
  @Serializable(with = SpendingKeysetSerializer::class)
  val activeSpendingKeyset: SpendingKeyset,
  val inactiveSpendingKeysets: List<
    @Serializable(with = SpendingKeysetSerializer::class)
    SpendingKeyset
  >,
  @Serializable(with = AppGlobalAuthKeypairSerializer::class)
  val appGlobalAuthKeypair: AppGlobalAuthKeypair,
  val appSpendingKeys: Map<
    @Serializable(with = AppSpendingPublicKeySerializer::class)
    AppSpendingPublicKey,
    @Serializable(with = AppSpendingPrivateKeySerializer::class)
    AppSpendingPrivateKey
  >,
)
