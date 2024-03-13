package build.wallet.database.adapters

import build.wallet.bitkey.socrec.DelegatedDecryptionKey
import build.wallet.crypto.CurveType

internal val DelegatedDecryptionKeyColumnAdapter = SocRecKeyColumnAdapter(
  factory = ::DelegatedDecryptionKey,
  keyCurve = CurveType.SECP256K1
)
