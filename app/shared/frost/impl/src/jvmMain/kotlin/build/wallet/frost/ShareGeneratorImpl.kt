package build.wallet.frost

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.rust.core.ShareGenerator as FfiShareGenerator

@BitkeyInject(AppScope::class)
class ShareGeneratorImpl : ShareGenerator {
  private val shareGenerator: FfiShareGenerator = FfiShareGenerator()

  override fun generate(): KeygenResult<UnsealedRequest> =
    runCatchingKeygenError {
      UnsealedRequest(value = shareGenerator.generate())
    }

  override fun aggregate(unsealedRequest: UnsealedRequest): KeygenResult<ShareDetails> =
    runCatchingKeygenError {
      ShareDetailsImpl(
        shareDetails = shareGenerator
          .aggregate(sealedResponse = unsealedRequest.value)
      )
    }

  override fun encode(shareDetails: ShareDetails): KeygenResult<UnsealedRequest> {
    require(shareDetails is ShareDetailsImpl)
    return runCatchingKeygenError {
      UnsealedRequest(shareGenerator.encodeCompleteDistributionRequest(shareDetails.shareDetails))
    }
  }
}
