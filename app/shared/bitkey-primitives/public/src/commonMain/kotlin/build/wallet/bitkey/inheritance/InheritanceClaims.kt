package build.wallet.bitkey.inheritance

/**
 * The set of inheritance claims relevant for the currently logged in account.
 */
data class InheritanceClaims(
  val benefactorClaims: List<BenefactorClaim>,
  val beneficiaryClaims: List<BeneficiaryClaim>,
) {
  companion object {
    val EMPTY = InheritanceClaims(
      benefactorClaims = emptyList(),
      beneficiaryClaims = emptyList()
    )
  }
}
