package build.wallet.recovery.socrec

import build.wallet.bitkey.socrec.SocRecKey
import build.wallet.crypto.CurveType

interface ProtectedCustomerIdentityKey : SocRecKey, CurveType.Curve25519
