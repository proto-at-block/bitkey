package build.wallet.bitkey.relationships

import build.wallet.bitkey.socrec.SocRecKey
import build.wallet.crypto.CurveType

/**
 * Recovery Contact public key if you're protecting other wallet, stored in the cloud backup
 * starting with V2.
 */
interface DelegatedDecryptionKey : SocRecKey, CurveType.Curve25519
