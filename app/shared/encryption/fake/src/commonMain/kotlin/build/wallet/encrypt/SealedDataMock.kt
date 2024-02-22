package build.wallet.encrypt

import okio.ByteString.Companion.decodeHex

val CIPHERTEXT_HEX = "deadbeef"
val NONCE_HEX = "abcdef"
val TAG_HEX = "123456"

val SealedDataMock =
  SealedData(
    ciphertext = CIPHERTEXT_HEX.decodeHex(),
    nonce = NONCE_HEX.decodeHex(),
    tag = TAG_HEX.decodeHex()
  )
