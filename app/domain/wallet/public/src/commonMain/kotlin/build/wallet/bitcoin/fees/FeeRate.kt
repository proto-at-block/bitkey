package build.wallet.bitcoin.fees

data class FeeRate(val satsPerVByte: Float) {
  companion object {
    val Fallback = FeeRate(satsPerVByte = 1f)
  }
}
