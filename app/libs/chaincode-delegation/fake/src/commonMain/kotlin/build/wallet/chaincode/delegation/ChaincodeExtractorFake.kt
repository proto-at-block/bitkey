package build.wallet.chaincode.delegation

import okio.ByteString
import okio.ByteString.Companion.decodeHex

class ChaincodeExtractorFake : ChaincodeExtractor {
  // Default fake chaincode (32 bytes of zeros)
  var extractChaincodeResult: ChaincodeDelegationResult<ByteString> =
    ChaincodeDelegationResult.Ok("0000000000000000000000000000000000000000000000000000000000000000".decodeHex())

  override fun extractChaincode(xpub: String): ChaincodeDelegationResult<ByteString> =
    extractChaincodeResult

  fun reset() {
    extractChaincodeResult = ChaincodeDelegationResult.Ok(
      "0000000000000000000000000000000000000000000000000000000000000000".decodeHex()
    )
  }
}
