package build.wallet.frost

import build.wallet.rust.core.ShareGenerator as FfiShareGenerator

class ShareGeneratorImpl(
  private val shareGenerator: FfiShareGenerator,
) : ShareGenerator {
  override fun generate(): KeygenResult<SharePackage> =
    runCatchingKeygenError {
      SharePackageImpl(ffiSharePackage = shareGenerator.generate())
    }

  override fun aggregate(
    peerSharePackage: SharePackage,
    peerKeyCommitments: KeyCommitments,
  ): KeygenResult<ShareDetails> =
    runCatchingKeygenError {
      require(peerSharePackage is SharePackageImpl)
      require(peerKeyCommitments is KeyCommitmentsImpl)

      ShareDetailsImpl(
        shareDetails = shareGenerator.aggregate(
          peerSharePackage = peerSharePackage.ffiSharePackage,
          peerKeyCommitments = peerKeyCommitments.ffiKeyCommitments
        )
      )
    }
}
