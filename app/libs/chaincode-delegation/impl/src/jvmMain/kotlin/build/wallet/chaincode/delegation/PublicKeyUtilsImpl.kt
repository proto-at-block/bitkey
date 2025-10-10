package build.wallet.chaincode.delegation

import build.wallet.bitcoin.keys.DescriptorPublicKey
import build.wallet.catchingResult
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import com.github.michaelbull.result.getOrElse
import build.wallet.rust.core.extractPublicKey as coreExtractPublicKey

@BitkeyInject(AppScope::class)
class PublicKeyUtilsImpl : PublicKeyUtils {
  override fun extractPublicKey(
    descriptorPublicKey: DescriptorPublicKey,
  ): ChaincodeDelegationResult<String> {
    val extractedPublicKey = catchingResult {
      coreExtractPublicKey(descriptorPublicKey.dpub)
    }.getOrElse {
      return ChaincodeDelegationResult.Err(ChaincodeDelegationError.PublicKeyExtraction(it, "failed to extract public key"))
    }

    return ChaincodeDelegationResult.Ok(extractedPublicKey)
  }
}
