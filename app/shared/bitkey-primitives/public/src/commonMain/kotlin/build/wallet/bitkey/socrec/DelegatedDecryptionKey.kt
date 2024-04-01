package build.wallet.bitkey.socrec

import build.wallet.crypto.CurveType

/**
 * Trusted contact public key if you're protecting other wallet, stored in the cloud backup
 * starting with V2.
 */
interface DelegatedDecryptionKey : SocRecKey, CurveType.Curve25519
