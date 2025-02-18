package build.wallet.frost

interface FrostSigner {
  fun generateSealedSignPsbtRequest(): SigningResult<String>

  fun signPsbt(unsealedResponse: UnsealedResponse): SigningResult<String>
}
