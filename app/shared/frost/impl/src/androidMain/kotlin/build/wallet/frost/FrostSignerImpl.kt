package build.wallet.frost

import build.wallet.rust.core.FrostSigner as FfiFrostSigner

class FrostSignerImpl(
  val ffiFrostSigner: FfiFrostSigner,
) : FrostSigner {
  override fun generateSealedSignPsbtRequest(): SigningResult<String> =
    runCatchingSigningError {
      ffiFrostSigner.signPsbtRequest()
    }

  override fun signPsbt(unsealedResponse: UnsealedResponse): SigningResult<String> =
    runCatchingSigningError {
      ffiFrostSigner.signPsbt(sealedResponse = unsealedResponse.value)
    }
}
