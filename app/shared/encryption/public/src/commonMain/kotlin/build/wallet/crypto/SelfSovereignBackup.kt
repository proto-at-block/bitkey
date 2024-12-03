package build.wallet.crypto

import build.wallet.encrypt.SealedData
import build.wallet.secureenclave.SePublicKey
import okio.ByteString

typealias SSBServerPlaintextKeyShare = ByteArray

data class SSBLocalWrappingPublicKeys(
  val lkaPub: SePublicKey,
  val lknPub: SePublicKey,
)

data class SSBServerBundle(
  val ephemeralPublicKey: ByteString,
  val sealedServerKeyShare: SealedData,
)

/**
 * Secure-enclave based encryption for self-sovereign backup.
 * https://assets.ctfassets.net/mtmp6hzjjvnd/6Qjcs8zgMiyffC0Uk8cx4V/380f331f5b4156622c8f5a376c352c96/Self-Custody_without_Hardware_-10-30-24-.pdf?ref=bitkey.build
 * See Section 4.1 and Appendix A.6 of the "Self-Custody without Hardware" whitepaper for more.
 *
 */
interface SelfSovereignBackup {
  /**
   * Generate local wrapping keys; LKA and LKN.
   * LKA is "local wrapping key with authentication requirements" and
   * LKN is "local wrapping key without authentication requirements".
   */
  fun generateLocalWrappingKeys(): SSBLocalWrappingPublicKeys

  /**
   * Rotate the LKN.
   */
  fun rotateLocalWrappingKeyWithoutAuth(): SSBLocalWrappingPublicKeys

  /**
   * Export the local wrapping public keys.
   */
  fun exportLocalWrappingPublicKeys(): SSBLocalWrappingPublicKeys

  /**
   * Decrypt the server key share.
   */
  fun decryptServerKeyShare(bundle: SSBServerBundle): SSBServerPlaintextKeyShare
}
