package build.wallet.chaincode.delegation

import build.wallet.catchingResult
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.toByteString
import com.github.michaelbull.result.getOrElse
import okio.ByteString
import build.wallet.rust.core.extractXpubChaincode as coreExtractXpubChaincode

@BitkeyInject(AppScope::class)
class ChaincodeExtractorImpl : ChaincodeExtractor {
  override fun extractChaincode(xpub: String): ChaincodeDelegationResult<ByteString> {
    val chaincodeBytes = catchingResult {
      coreExtractXpubChaincode(xpub)
    }.getOrElse {
      return ChaincodeDelegationResult.Err(
        ChaincodeDelegationError.XpubChaincodeExtraction(it, "failed to extract chaincode from xpub")
      )
    }

    return ChaincodeDelegationResult.Ok(chaincodeBytes.toByteString())
  }
}
