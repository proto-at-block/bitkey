package build.wallet.encrypt

import build.wallet.crypto.SymmetricKey
import build.wallet.crypto.SymmetricKeyImpl
import okio.ByteString.Companion.toByteString
import platform.Foundation.NSData

/**
 * Interop helper to create [SymmetricKey] from [NSData].
 */
@Suppress("unused") // used by iOS code
fun SymmetricKey(data: NSData): SymmetricKey {
  return SymmetricKeyImpl(data.toByteString())
}

/**
 * Interop helper to convert KMP [SymmetricKey] type to native [NSData].
 */
@Suppress("unused") // used by iOS code
fun SymmetricKey.toData(): NSData = raw.toByteArray().toData()
