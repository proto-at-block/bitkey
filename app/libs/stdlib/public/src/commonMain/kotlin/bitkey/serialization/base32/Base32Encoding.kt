package bitkey.serialization.base32

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import okio.ByteString

object Base32Encoding {
  /**
   * Encodes the input byte array into a Crockford Base32 string.
   *
   * @param input the byte array to encode
   * @return the Base32 encoded string
   */
  fun encode(input: ByteString): Result<String, Throwable> {
    return Ok(Base32.encode(input, Base32.Alphabet.Crockford))
  }

  /**
   * Decodes the input Crockford Base32 string into a byte array.
   *
   * @param input the Base32 encoded string
   * @return the decoded byte array
   */
  fun decode(input: String): Result<ByteString, Throwable> {
    // Preserve the former decoder's lenient Crockford behavior.
    val normalizedInput = input.filterNot { it == '-' || it.isBase32Whitespace() }
    val hasTruncatedFinalByte = when (normalizedInput.length % 8) {
      1, 3, 6 -> true
      else -> false
    }
    if ('=' in normalizedInput || hasTruncatedFinalByte) {
      return Err(Base32.Base32Error("Invalid Crockford Base32 input"))
    }
    return Base32.decode(normalizedInput, Base32.Alphabet.Crockford)
  }

  private fun Char.isBase32Whitespace(): Boolean =
    this == '\n' || this == '\r' || this == ' ' || this == '\t'

  const val BITS_PER_CHAR = 5
}
