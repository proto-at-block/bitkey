package bitkey.serialization.base32

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import okio.ByteString
import okio.ByteString.Companion.toByteString

object Base32 {
  data class Base32Error(override val message: String) : Throwable(message)

  sealed class Alphabet {
    data class RFC4648(val padding: Boolean) : Alphabet()

    data object Crockford : Alphabet()
  }

  private val rfc4648Alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".encodeToByteArray()
  val crockfordAlphabet = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".encodeToByteArray()

  // -1 values represent invalid characters.
  private val rfc4648InvAlphabet = byteArrayOf(
    -1, -1, 26, 27, 28, 29, 30, 31, -1, -1, -1, -1, -1, 0, -1, -1, -1, 0, 1, 2, 3, 4, 5, 6, 7, 8,
    9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25
  )
  private val crockfordInvAlphabet = byteArrayOf(
    0, 1, 2, 3, 4, 5, 6, 7, 8, 9, -1, -1, -1, -1, -1, -1, -1, 10, 11, 12, 13, 14, 15, 16, 17, 1,
    18, 19, 1, 20, 21, 0, 22, 23, 24, 25, 26, -1, 27, 28, 29, 30, 31
  )

  fun encode(
    input: ByteString,
    alphabet: Alphabet = Alphabet.Crockford,
  ): String {
    val (alphabetBytes, padding) = when (alphabet) {
      is Alphabet.RFC4648 -> rfc4648Alphabet to alphabet.padding
      is Alphabet.Crockford -> crockfordAlphabet to false
    }

    // "The encoding process represents 40-bit groups of input bits as output
    // strings of 8 encoded characters.  Proceeding from left to right, a
    // 40-bit input group is formed by concatenating 5 8bit input groups.
    // These 40 bits are then treated as 8 concatenated 5-bit groups, each
    // of which is translated into a single character in the base 32
    // alphabet." - RFC 4648, section 6
    val outputBytes = input.toByteArray().asIterable().chunked(5).flatMap { chunk ->
      val buffer = chunk.toByteArray().copyOf(5).toUByteArray()
      listOf(
        (alphabetBytes[((buffer[0].toInt() and 0xF8) shr 3)]),
        (alphabetBytes[(((buffer[0].toInt() and 0x07) shl 2) or ((buffer[1].toInt() and 0xC0) shr 6))]),
        (alphabetBytes[((buffer[1].toInt() and 0x3E) shr 1)]),
        (alphabetBytes[(((buffer[1].toInt() and 0x01) shl 4) or ((buffer[2].toInt() and 0xF0) shr 4))]),
        (alphabetBytes[(((buffer[2].toInt() and 0x0F) shl 1) or (buffer[3].toInt() shr 7))]),
        (alphabetBytes[((buffer[3].toInt() and 0x7C) shr 2)]),
        (alphabetBytes[(((buffer[3].toInt() and 0x03) shl 3) or ((buffer[4].toInt() and 0xE0) shr 5))]),
        (alphabetBytes[(buffer[4].toInt() and 0x1F)])
      )
    }.toMutableList()

    // Handle padding for the final chunk if the input size is not a multiple of 5.
    if (input.size % 5 != 0) {
      val len = outputBytes.size
      // Calculate the number of padding characters needed for the final encoded block.
      //
      // This is done by first determining how many bytes are in the incomplete final chunk
      // (input.size % 5), then calculating the total bits in this chunk by multiplying by 8 (since
      // each byte has 8 bits). The formula then adds 4 before dividing by 5 to ensure we round up
      // to the nearest whole number of 5-bit groups that can be represented by Base32 characters.
      // This calculation tells us how many of the 8 possible Base32 characters are used by the
      // final chunk of bits. By subtracting this number from 8, we find out how many extra padding
      // characters ('=') are needed to complete the last encoded block to its full size of 8
      // characters. This ensures that the encoded output aligns with Base32 padding requirements
      // when the input data size is not a multiple of 5 bytes.
      val numExtra = 8 - (input.size % 5 * 8 + 4) / 5
      if (padding) {
        // If padding is enabled, replace the last few characters with '='.
        for (i in 1..numExtra) {
          outputBytes[len - i] = '='.code.toByte()
        }
      } else {
        // If padding is not needed, remove the extra characters.
        outputBytes.subList(len - numExtra, len).clear()
      }
    }

    return outputBytes.toByteArray().decodeToString()
  }

  fun decode(
    input: String,
    alphabet: Alphabet = Alphabet.Crockford,
  ): Result<ByteString, Base32Error> {
    // Return an error if the input contains non-ASCII characters.
    if (input.any { it.code > 127 }) {
      return Err(Base32Error("Input contains non-ASCII characters"))
    }

    val alphabetArray = when (alphabet) {
      is Alphabet.RFC4648 -> rfc4648InvAlphabet
      is Alphabet.Crockford -> crockfordInvAlphabet
    }

    // Remove padding and convert to uppercase to normalize the input.
    val dataBytes = input.trimEnd('=').uppercase()
    // Calculate the output length in bytes. Base32 encoding maps every 5 bits of binary data
    // to a single character from the Base32 alphabet. Therefore, to determine the length of the
    // output byte array (the decoded binary data), we multiply the length of the Base32 encoded
    // string by 5 (since each character represents 5 bits) and then divide by 8 (because there
    // are 8 bits in a byte).
    val outputLength = dataBytes.length * 5 / 8
    val outputBytes = ByteArray(outputLength)

    // When encoding binary data into Base32, we group the bits into sets of 5 and map each group
    // to one of 32 characters in the Base32 alphabet. For decoding, we reverse this process. To
    // efficiently convert back to the original binary data, we process the encoded string in chunks
    // of 8 characters. Each character represents 5 bits, so 8 characters represent 40 bits in
    // total.
    dataBytes.chunked(8).forEachIndexed { index, chunk ->
      // Map each character in the chunk to its corresponding value in the alphabet.
      val buffer = chunk.map {
        val alphabetIndex = it.code - '0'.code
        val value = alphabetArray.getOrNull(alphabetIndex)
        // Return an error if the character is not in the alphabet.
        if (value == null || value.toInt() == -1) return Err(Base32Error("Invalid character in input"))
        value.toInt()
      }

      // Decode the 8-character chunk into 5 bytes and write them to the output array.
      val byteIndex = index * 5
      buffer.let {
        if (byteIndex < outputBytes.size) outputBytes[byteIndex] = ((it[0] shl 3) or (it[1] ushr 2)).toByte()
        if (byteIndex + 1 < outputBytes.size) outputBytes[byteIndex + 1] = (((it[1] shl 6) or (it[2] shl 1) or (it[3] ushr 4)).toByte())
        if (byteIndex + 2 < outputBytes.size) outputBytes[byteIndex + 2] = (((it[3] shl 4) or (it[4] ushr 1)).toByte())
        if (byteIndex + 3 < outputBytes.size) outputBytes[byteIndex + 3] = (((it[4] shl 7) or (it[5] shl 2) or (it[6] ushr 3)).toByte())
        if (byteIndex + 4 < outputBytes.size) outputBytes[byteIndex + 4] = (((it[6] shl 5) or it[7]).toByte())
      }
    }

    return Ok(outputBytes.copyOfRange(0, outputLength).toByteString())
  }
}
