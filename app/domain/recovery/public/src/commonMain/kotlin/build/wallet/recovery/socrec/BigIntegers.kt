package build.wallet.recovery.socrec

import com.ionspin.kotlin.bignum.integer.BigInteger

/**
 * Converts a BigInteger to a fixed size byte array with padding on the left.
 */
fun BigInteger.toFixedSizeByteArray(size: Int): ByteArray {
  val byteArray = this.toByteArray()
  val padding = size - byteArray.size
  return if (padding > 0) {
    ByteArray(size) { index ->
      if (index < padding) {
        0
      } else {
        byteArray[index - padding]
      }
    }
  } else {
    byteArray
  }
}
