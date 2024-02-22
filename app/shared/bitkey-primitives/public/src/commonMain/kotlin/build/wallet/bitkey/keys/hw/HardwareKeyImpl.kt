package build.wallet.bitkey.keys.hw

import build.wallet.crypto.CurveType
import build.wallet.crypto.PublicKey

data class HardwareKeyImpl(
  override val curveType: CurveType,
  override val publicKey: PublicKey,
) : HardwareKey
