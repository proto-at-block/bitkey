package build.wallet.cloud.backup.csek

import build.wallet.crypto.SymmetricKeyImpl
import okio.ByteString.Companion.decodeHex

private val SEK_HEX = "b8ef0c208d341bf262638a7ecf142bea1234567890abcdef1234567890abcdef"
private val SEALED_NONCE_HEX = "0102030405060708090a0b0c"
private val SEALED_TAG_HEX = "00112233445566778899aabbccddeeff"
private val SEALED_SEK_PROTO_HEX =
  "0a20${SEK_HEX}120c${SEALED_NONCE_HEX}1a10${SEALED_TAG_HEX}"

val SealedCsekFake = SEALED_SEK_PROTO_HEX.decodeHex()
val CsekFake = Csek(key = SymmetricKeyImpl(raw = SEK_HEX.decodeHex()))

val SealedSsekFake = SEALED_SEK_PROTO_HEX.decodeHex()
val SsekFake = Csek(key = SymmetricKeyImpl(raw = SEK_HEX.decodeHex()))
