package build.wallet

import okio.ByteString
import okio.ByteString.Companion.toByteString

/** Convert [List] of [UByte] to [ByteArray]. */
fun List<UByte>.toByteArray(): ByteArray = toUByteArray().asByteArray()

/** Convert [List] of [UByte] to [ByteString]. */
fun List<UByte>.toByteString(): ByteString = toByteArray().toByteString()

/** Convert [ByteArray] to [List] of [UByte]. */
fun ByteArray.toUByteList(): List<UByte> = toUByteArray().asList()

/** Convert [ByteString] to [List] of [UByte]. */
fun ByteString.toUByteList(): List<UByte> = toByteArray().toUByteList()
