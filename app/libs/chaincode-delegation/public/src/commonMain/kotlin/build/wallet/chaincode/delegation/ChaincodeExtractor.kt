package build.wallet.chaincode.delegation

import okio.ByteString

/**
 * Utility for extracting components from extended public keys (xpubs)
 */
interface ChaincodeExtractor {
  fun extractChaincode(xpub: String): ChaincodeDelegationResult<ByteString>
}
