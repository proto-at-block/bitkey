package build.wallet.frost

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.rust.core.FrostSigner as FfiFrostSigner
import build.wallet.rust.core.ShareDetails as FfiShareDetails

@BitkeyInject(AppScope::class)
class FrostSignerFactoryImpl : FrostSignerFactory {
  override fun create(
    psbt: String,
    shareDetails: ShareDetails,
  ): SigningResult<FrostSigner> {
    require(shareDetails is ShareDetailsImpl) {
      "Received unexpected concrete implementation of ShareDetails."
    }

    return runCatchingSigningError {
      FrostSignerImpl(
        ffiFrostSigner = FfiFrostSigner(
          psbt = psbt,
          shareDetails = FfiShareDetails(
            secretShare = shareDetails.secretShare,
            keyCommitments = shareDetails.keyCommitments.ffiKeyCommitments
          )
        )
      )
    }
  }
}
