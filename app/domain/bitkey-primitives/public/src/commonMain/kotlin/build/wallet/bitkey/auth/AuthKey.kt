package build.wallet.bitkey.auth

import build.wallet.crypto.CurveType
import build.wallet.crypto.KeyPurpose

/**
 * Super-type for all authentication keys.
 */
interface AuthKey : KeyPurpose, CurveType.Secp256K1
