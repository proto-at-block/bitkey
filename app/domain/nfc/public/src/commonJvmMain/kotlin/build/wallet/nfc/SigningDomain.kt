package build.wallet.nfc

/**
 * Public interface of `SigningDomain`, equivalent to `core`'s `SigningDomain.
 */
enum class SigningDomain {
  AUTH,
  CONFIG,
  SPEND,
}
