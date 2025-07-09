package build.wallet.cloud.backup.csek

import build.wallet.crypto.SymmetricKeyImpl
import okio.ByteString.Companion.decodeHex

private val SEK_HEX = "b8ef0c208d341bf262638a7ecf142bea1234567890abcdef1234567890abcdef"

val SealedCsekFake = SEK_HEX.decodeHex()
val CsekFake = Csek(key = SymmetricKeyImpl(raw = SEK_HEX.decodeHex()))

val SealedSsekFake = SEK_HEX.decodeHex()
val SsekFake = Csek(key = SymmetricKeyImpl(raw = SEK_HEX.decodeHex()))
