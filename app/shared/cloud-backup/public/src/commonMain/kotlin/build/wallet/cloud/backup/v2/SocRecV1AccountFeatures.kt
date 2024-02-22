package build.wallet.cloud.backup.v2

import build.wallet.crypto.PublicKey
import build.wallet.encrypt.XCiphertext

/**
 * Account features relevant to the V1 implementation of Social Recovery.
 */
interface SocRecV1AccountFeatures {
  /**
   * Key information about trusted contact used to restore transferred
   * keys during recovery.
   */
  val socRecEncryptionKeyCiphertextMap: Map<String, XCiphertext>

  /**
   * Encrypted key information for the account
   */
  val socRecFullAccountKeysCiphertext: XCiphertext

  /**
   * Identity key for the protected customer role. We only store the public key and only
   * use it for social challenge verification.
   */
  val protectedCustomerIdentityPublicKey: PublicKey
}
