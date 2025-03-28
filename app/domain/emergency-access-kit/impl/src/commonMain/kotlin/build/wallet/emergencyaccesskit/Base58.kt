/*
Originally from: https://github.com/komputing/KBase58

MIT License

Copyright (c) 2019 Komputing

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
 */
package build.wallet.emergencyaccesskit

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.ByteString.Companion.toByteString
import kotlin.coroutines.cancellation.CancellationException

private const val ENCODED_ZERO = '1'
private const val CHECKSUM_SIZE = 4

private const val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
private val alphabetIndices by lazy {
  IntArray(128) { ALPHABET.indexOf(it.toChar()) }
}

/**
 * Encodes the bytes as a base58 string (no checksum is appended).
 *
 * @return the base58-encoded string
 */
internal suspend fun ByteArray.encodeToBase58String(): String {
  return withContext(Dispatchers.Default) {
    val input = copyOf(size) // since we modify it in-place
    if (input.isEmpty()) {
      ""
    } else {
      // Count leading zeros.
      var zeros = 0
      while (zeros < input.size && input[zeros].toInt() == 0) {
        ++zeros
      }
      // Convert base-256 digits to base-58 digits (plus conversion to ASCII characters)
      val encoded = CharArray(input.size * 2) // upper bound
      var outputStart = encoded.size
      var inputStart = zeros
      while (inputStart < input.size) {
        encoded[--outputStart] =
          ALPHABET[divmod(input, inputStart.toUInt(), 256.toUInt(), 58.toUInt()).toInt()]
        if (input[inputStart].toInt() == 0) {
          ++inputStart // optimization - skip leading zeros
        }
      }
      // Preserve exactly as many leading encoded zeros in output as there were leading zeros in data.
      while (outputStart < encoded.size && encoded[outputStart] == ENCODED_ZERO) {
        ++outputStart
      }
      while (--zeros >= 0) {
        encoded[--outputStart] = ENCODED_ZERO
      }
      // Return encoded string (including encoded leading zeros).
      encoded.concatToString(outputStart, encoded.size)
    }
  }
}

internal suspend fun String.decodeBase58WithChecksum(): ByteArray {
  return withContext(Dispatchers.Default) {
    val rawBytes = decodeBase58()
    require(rawBytes.size >= CHECKSUM_SIZE) {
      "Too short for checksum: $this l:  ${rawBytes.size}"
    }
    val checksum = rawBytes.copyOfRange(rawBytes.size - CHECKSUM_SIZE, rawBytes.size)

    val payload = rawBytes.copyOfRange(0, rawBytes.size - CHECKSUM_SIZE)

    val hash = payload.toByteString().sha256().sha256().toByteArray()
    val computedChecksum = hash.copyOfRange(0, CHECKSUM_SIZE)

    require(checksum.contentEquals(computedChecksum)) {
      "Checksum mismatch: $checksum is not computed checksum $computedChecksum"
    }
    payload
  }
}

/**
 * Decodes the base58 string into a [ByteArray]
 *
 * @return the decoded data bytes
 * @throws NumberFormatException if the string is not a valid base58 string
 */
@Throws(NumberFormatException::class, CancellationException::class)
internal suspend fun String.decodeBase58(): ByteArray {
  return withContext(Dispatchers.Default) {
    if (isEmpty()) {
      ByteArray(0)
    } else {
      // Convert the base58-encoded ASCII chars to a base58 byte sequence (base58 digits).
      val input58 = ByteArray(length)
      for (i in indices) {
        val c = this@decodeBase58[i]
        val digit = if (c.code < 128) alphabetIndices[c.code] else -1
        if (digit < 0) {
          throw NumberFormatException("Illegal character $c at position $i")
        }
        input58[i] = digit.toByte()
      }
      // Count leading zeros.
      var zeros = 0
      while (zeros < input58.size && input58[zeros].toInt() == 0) {
        ++zeros
      }
      // Convert base-58 digits to base-256 digits.
      val decoded = ByteArray(length)
      var outputStart = decoded.size
      var inputStart = zeros
      while (inputStart < input58.size) {
        decoded[--outputStart] =
          divmod(input58, inputStart.toUInt(), 58.toUInt(), 256.toUInt()).toByte()
        if (input58[inputStart].toInt() == 0) {
          ++inputStart // optimization - skip leading zeros
        }
      }
      // Ignore extra leading zeroes that were added during the calculation.
      while (outputStart < decoded.size && decoded[outputStart].toInt() == 0) {
        ++outputStart
      }
      // Return decoded data (including original number of leading zeros).
      decoded.copyOfRange(outputStart - zeros, decoded.size)
    }
  }
}

/**
 * Divides a number, represented as an array of bytes each containing a single digit
 * in the specified base, by the given divisor. The given number is modified in-place
 * to contain the quotient, and the return value is the remainder.
 *
 * @param number     the number to divide
 * @param firstDigit the index within the array of the first non-zero digit
 * (this is used for optimization by skipping the leading zeros)
 * @param base       the base in which the number's digits are represented (up to 256)
 * @param divisor    the number to divide by (up to 256)
 * @return the remainder of the division operation
 */
private fun divmod(
  number: ByteArray,
  firstDigit: UInt,
  base: UInt,
  divisor: UInt,
): UInt {
  // this is just long division which accounts for the base of the input digits
  var remainder = 0.toUInt()
  for (i in firstDigit until number.size.toUInt()) {
    val digit = number[i.toInt()].toUByte()
    val temp = remainder * base + digit
    number[i.toInt()] = (temp / divisor).toByte()
    remainder = temp % divisor
  }
  return remainder
}
