package build.wallet.cloud.backup.v2

import build.wallet.encrypt.XCiphertext

/**
 * Account features relevant to the V1 implementation of Social Recovery.
 */
interface SocRecV1AccountFeatures {
  /**
   * Key information about Recovery Contact used to restore transferred
   * keys during recovery.
   */
  val socRecSealedDekMap: Map<String, XCiphertext>

  /**
   * Encrypted key information for the account
   */
  val socRecSealedFullAccountKeys: XCiphertext
}
