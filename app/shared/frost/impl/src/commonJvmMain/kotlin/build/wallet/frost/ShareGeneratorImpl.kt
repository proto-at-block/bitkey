package build.wallet.frost

import build.wallet.rust.core.ShareGenerator as FfiShareGenerator

internal data class ShareGeneratorImpl(
  private val shareGenerator: FfiShareGenerator = FfiShareGenerator(),
) : ShareGenerator {
  override fun generate(): KeygenResult<SealedRequest> =
    runCatchingKeygenError {
      SealedRequest(value = shareGenerator.generate())
    }

  override fun aggregate(sealedRequest: SealedRequest): KeygenResult<ShareDetails> =
    runCatchingKeygenError {
      ShareDetailsImpl(shareDetails = shareGenerator.aggregate(sealedResponse = sealedRequest.value))
    }

  override fun encode(shareDetails: ShareDetails): KeygenResult<SealedRequest> {
    require(shareDetails is ShareDetailsImpl)
    return runCatchingKeygenError {
      SealedRequest(shareGenerator.encodeCompleteDistributionRequest(shareDetails.shareDetails))
    }
  }
}
