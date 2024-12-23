package build.wallet.serialization

import build.wallet.catchingResult
import com.github.michaelbull.result.Result
import io.matthewnelson.encoding.base32.Base32Crockford
import io.matthewnelson.encoding.core.Decoder.Companion.decodeToByteArray
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import okio.ByteString
import okio.ByteString.Companion.toByteString

object Base32Encoding {
  // Crockford Base32 encoding settings
  private val base32Crockford = Base32Crockford {
    // Don't use hyphens
    this.hyphenInterval = 0
  }

  /**
   * Encodes the input byte array into a Crockford Base32 string.
   *
   * @param input the byte array to encode
   * @return the Base32 encoded string
   */
  fun encode(input: ByteString): Result<String, Throwable> {
    return catchingResult {
      input.toByteArray().encodeToString(base32Crockford)
    }
  }

  /**
   * Decodes the input Crockford Base32 string into a byte array.
   *
   * @param input the Base32 encoded string
   * @return the decoded byte array
   */
  fun decode(input: String): Result<ByteString, Throwable> {
    return catchingResult {
      input.decodeToByteArray(base32Crockford).toByteString()
    }
  }

  const val BITS_PER_CHAR = 5
}
