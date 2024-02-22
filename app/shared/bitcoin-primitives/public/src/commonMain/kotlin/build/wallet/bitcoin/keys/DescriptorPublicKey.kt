package build.wallet.bitcoin.keys

/**
 * A DescriptorPublicKey (dpub) that contains a spending key xpub with metadata.
 * See https://github.com/bitcoin/bips/blob/master/bip-0380.mediawiki#key-expressions for details
 */
data class DescriptorPublicKey(
  val origin: Origin,
  val xpub: String,
  val derivationPath: String,
  val wildcard: Wildcard,
) {
  val dpub: String = "${origin.raw}$xpub$derivationPath"

  /**
   * Key origin information
   */
  data class Origin(
    val fingerprint: String,
    val derivationPath: String,
  ) {
    val raw: String = "[$fingerprint$derivationPath]"
  }

  /**
   * Whether a descriptor has a wildcard in it
   */
  enum class Wildcard {
    /** No wildcard */
    None,

    /** Unhardened wildcard, e.g. * */
    Unhardened,

    /** Hardened wildcard, e.g. *h */
    Hardened,
  }

  companion object {
    /**
     * Parses xpubs ONLY according to BIP-380 Key Expressions
     * https://github.com/bitcoin/bips/blob/master/bip-0380.mediawiki#key-expressions
     * In addition, we require Origin to be present, because our own keys always include origin.
     * The spec, however, allows Origin to be optional.
     */
    operator fun invoke(dpub: String): DescriptorPublicKey {
      val matches = dpubPattern.matchEntire(dpub)
      requireNotNull(matches) { "not a dpub: $dpub" }

      val fingerprint = matches.groupValues[1]
      val originDerivation = matches.groupValues[2]
      val xpub = matches.groupValues[3]
      val derivationPath = matches.groupValues[4]
      val finalStep = matches.groupValues[5]

      require(fingerprint.isNotEmpty()) { "spending descriptor missing origin information" }

      val wildcard =
        when (finalStep) {
          "/*" -> Wildcard.Unhardened
          "/*h", "/*H", "/*'" -> Wildcard.Hardened
          else -> Wildcard.None
        }

      require(
        xpub.startsWith("xpub") || xpub.startsWith("tpub")
      ) {
        "invalid xpub: $xpub when parsing dpub: $dpub"
      }

      return DescriptorPublicKey(
        origin = Origin(fingerprint, originDerivation),
        xpub = xpub,
        derivationPath = derivationPath + finalStep,
        wildcard = wildcard
      )
    }

    private val dpubPattern =
      Regex(
        """(?:\[([0-9a-fA-F]{8})((?:/[0-9]+[hH']?)*)])?([a-zA-Z0-9]{111,112})((?:/[0-9]+[hH']?)*)(/\*[hH']?)?"""
      )
  }
}
