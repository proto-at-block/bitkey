package build.wallet.frost

interface FrostSignerFactory {
  fun create(
    psbt: String,
    shareDetails: ShareDetails,
  ): SigningResult<FrostSigner>
}
