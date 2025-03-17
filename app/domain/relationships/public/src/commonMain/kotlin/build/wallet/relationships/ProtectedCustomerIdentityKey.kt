package build.wallet.relationships

import build.wallet.bitkey.socrec.SocRecKey
import build.wallet.crypto.CurveType

interface ProtectedCustomerIdentityKey : SocRecKey, CurveType.Curve25519
