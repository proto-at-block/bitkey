package bitkey.serialization.checksum

object CRC6 {
  /**
   * Calculates the CRC6 checksum of the given byte array. Using the polynomial 0x27
   * according to CRC-6/CDMA2000-A.
   *
   * This check should have the following properties
   *  - detect any 2 bit errors for data up to 57 bits
   *  - detect any single burst of errors up to 6 bits for any data length
   *    for base32 encoded data, it means that it can detect an error in any 1 character
   *  - detect a fraction of longer burst errors of n bits given by (1-2^-n)
   *
   * @param data The byte array to calculate the CRC6 checksum for.
   * @return The 6 bit CRC6 checksum. The 2 MSB in the byte are always 0.
   */
  fun calculate(data: ByteArray): Byte {
    var crc = 0x3F // Initial CRC value
    val polynomial = 0x27 // CRC polynomial

    data.forEach { char ->
      val temp = char.toInt()
      for (i in 0 until 8) {
        // Shift left to start with the MSB
        val bit = (temp ushr (7 - i)) and 1
        val msb = (crc ushr 5) and 1
        crc = crc shl 1 // Shift CRC to the left
        crc = crc and 0x3F // Ensure CRC stays 6-bit

        if (bit xor msb == 1) {
          crc = crc xor polynomial
        }
      }
    }

    return (crc and 0x3F).toByte()
  }
}
