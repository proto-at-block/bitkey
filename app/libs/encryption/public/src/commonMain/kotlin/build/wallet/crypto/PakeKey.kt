package build.wallet.crypto

/**
 * Represents a key used for PAKE authentication.
 */
interface PakeKey : KeyPurpose, CurveType.Curve25519
