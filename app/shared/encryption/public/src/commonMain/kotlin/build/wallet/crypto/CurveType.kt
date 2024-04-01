package build.wallet.crypto

/**
 * The type of curve used for the elliptic key.
 */
sealed interface CurveType {
  interface Secp256K1 : CurveType

  interface Curve25519 : CurveType
}
