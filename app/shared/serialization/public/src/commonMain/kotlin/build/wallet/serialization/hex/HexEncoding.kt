package build.wallet.serialization.hex

import build.wallet.catching
import com.github.michaelbull.result.Result
import okio.ByteString
import okio.ByteString.Companion.decodeHex

/**
 * Non-throwing version of [decodeHex].
 * Decodes a hex string to a [ByteString] and returns [Result]. In case of decoding error, [Err] is
 * returned with the exception.
 */
fun String.decodeHexWithResult(): Result<ByteString, Throwable> = Result.catching { decodeHex() }
