package build.wallet.chaincode.delegation

import build.wallet.bitcoin.keys.DescriptorPublicKey

/**
 * Utility for convenience functions that use Rust to manipulate public keys
 */
interface PublicKeyUtils {
  /**
   * Extract a public key from a descriptor public key for use in chaincode delegated onboarding
   *
   * @param descriptorPublicKey

   */
  fun extractPublicKey(descriptorPublicKey: DescriptorPublicKey): ChaincodeDelegationResult<String>
}
