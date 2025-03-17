package build.wallet.encrypt

import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.memScoped
import okio.ByteString
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.toByteString
import platform.Foundation.NSData
import platform.Foundation.create

/**
 * Interop helper to create KMP okio [ByteString] from native [NSData].
 */
fun ByteString(data: NSData): ByteString = data.toByteString()

/**
 * Interop helper to create native [NSData] from KMP okio [ByteString].
 */
fun ByteString.toData(): NSData = toByteArray().toData()

/**
 * Throwing interop helper to create native [NSData] from a hex [String].
 */
@Throws(Throwable::class)
fun decodeHex(s: String): NSData = s.decodeHex().toByteArray().toData()

/**
 * Interop helper to create native [NSData] from Kotlin [ByteArray].
 */
internal fun ByteArray.toData(): NSData =
  memScoped {
    NSData.create(
      bytes = allocArrayOf(this@toData),
      length = this@toData.size.toULong()
    )
  }
