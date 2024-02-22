package build.wallet.cloud.backup.csek

import build.wallet.crypto.SymmetricKeyImpl
import okio.ByteString.Companion.decodeHex

val CSEK_HEX = "b8ef0c208d341bf262638a7ecf142bea"

val SealedCsekFake = CSEK_HEX.decodeHex()
val CsekFake = Csek(key = SymmetricKeyImpl(raw = CSEK_HEX.decodeHex()))
